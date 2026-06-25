package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.AnswerItem;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationLookupTokenService;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.service.ParticipantService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPendingEvent;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private GatheringRepository gatheringRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParticipantService participantService;

    @Mock
    private AuthService authService;

    @Mock
    private FormRepository formRepository;

    @Mock
    private FormQuestionRepository formQuestionRepository;

    @Mock
    private ApplicationAnswerRepository applicationAnswerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TicketService ticketService;

    @Mock
    private TicketTransactionRepository ticketTransactionRepository;

    @Mock
    private ApplicationLookupTokenService applicationLookupTokenService;

    @InjectMocks
    private ApplicationService applicationService;

    private UUID gatheringId;
    private UUID userId;
    private UUID applicationId;
    private Gathering gathering;
    private User user;
    private ApplicationRequest request;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        userId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(10)
                .build();

        user = User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("김철수")
                .gender(Gender.MALE)
                .age(28)
                .nickname("chulsoo")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);

        request = new ApplicationRequest();
        setAnswers(request, List.of());
    }

    // ── apply() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("회원 정상 신청")
    void apply_member_success() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(participantService.getOrCreateForUser(any())).willReturn(Participant.member(user));
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(gatheringId, request, userId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        then(eventPublisher).should().publishEvent(any(ApplicationPendingEvent.class));
    }

    @Test
    @DisplayName("승인된 우연한 식탁 회원은 이용권 차감 후 자동 확정된다 (KAN-277)")
    void apply_randomTable_approvedMember_autoConfirmsWithTicket() {
        Gathering randomTable = Gathering.builder()
                .title("우연한 식탁")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(4)
                .build();
        ReflectionTestUtils.setField(randomTable, "gatheringType", GatheringType.RANDOM_TABLE);

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(randomTable));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        Participant participant = Participant.member(user);
        participant.approveRandomTable();
        given(participantService.getOrCreateForUser(any())).willReturn(participant);
        given(ticketService.tryUseOneTicket(eq(participant), any(Application.class))).willReturn(true);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(gatheringId, request, userId);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(ticketService).should().tryUseOneTicket(eq(participant), any(Application.class));
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("승인된 우연한 식탁 회원에게 이용권이 없으면 재심사 없이 결제 대기한다 (KAN-277)")
    void apply_randomTable_approvedMemberWithoutTicket_awaitsPayment() {
        Gathering randomTable = Gathering.builder()
                .title("우연한 식탁").eventDate(LocalDate.now().plusDays(7)).maxAttendees(4)
                .gatheringType(GatheringType.RANDOM_TABLE).build();
        Participant participant = Participant.member(user);
        participant.approveRandomTable();

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(randomTable));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(participantService.getOrCreateForUser(user)).willReturn(participant);
        given(ticketService.tryUseOneTicket(eq(participant), any(Application.class))).willReturn(false);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(gatheringId, request, userId);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("무료 우연한 식탁은 승인된 회원 신청 시 이용권 차감 없이 자동 확정된다")
    void apply_freeRandomTable_approvedMember_confirmsWithoutTicket() {
        Gathering randomTable = Gathering.builder()
                .title("무료 우연한 식탁")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(4)
                .price(0)
                .gatheringType(GatheringType.RANDOM_TABLE)
                .build();
        Participant participant = Participant.member(user);
        participant.approveRandomTable();

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(randomTable));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(participantService.getOrCreateForUser(user)).willReturn(participant);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(gatheringId, request, userId);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(ticketService).should(never()).tryUseOneTicket(eq(participant), any(Application.class));
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("거절된 참가자는 우연한 식탁을 다시 신청할 수 없다 (KAN-277)")
    void apply_randomTable_rejectedMember_isBlocked() {
        Gathering randomTable = Gathering.builder()
                .title("우연한 식탁").eventDate(LocalDate.now().plusDays(7)).maxAttendees(4)
                .gatheringType(GatheringType.RANDOM_TABLE).build();
        Participant participant = Participant.member(user);
        participant.rejectRandomTable();

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(randomTable));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(participantService.getOrCreateForUser(user)).willReturn(participant);

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RANDOM_TABLE_ELIGIBILITY_RESTRICTED);
    }

    @Test
    @DisplayName("비회원 정상 신청")
    void apply_guest_success() {
        FormQuestion phoneQuestion = question("phone", false);
        FormQuestion emailQuestion = question("email", false);
        setAnswers(request, List.of(
                answerItem(phoneQuestion.getId(), "01098765432"),
                answerItem(emailQuestion.getId(), "g@test.com")));

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any()))
                .willReturn(List.of(phoneQuestion, emailQuestion));
        given(applicationRepository.existsByGatheringIdAndPhoneAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(authService.isGuestEmailVerified("g@test.com")).willReturn(true);
        given(participantService.getOrCreateVerifiedGuest(any(), any(), any())).willReturn(Participant.guest("비회원", "g@test.com", "01098765432"));
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(gatheringId, request, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        then(eventPublisher).should().publishEvent(any(ApplicationPendingEvent.class));
        then(authService).should().consumeGuestEmailVerification("g@test.com");
    }

    @Test
    @DisplayName("비회원 이메일이 인증되지 않으면 신청을 차단한다")
    void apply_guestEmailNotVerified_throws() {
        FormQuestion phoneQuestion = question("phone", false);
        FormQuestion emailQuestion = question("email", false);
        setAnswers(request, List.of(
                answerItem(phoneQuestion.getId(), "01098765432"),
                answerItem(emailQuestion.getId(), "g@test.com")));
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any()))
                .willReturn(List.of(phoneQuestion, emailQuestion));
        given(applicationRepository.existsByGatheringIdAndPhoneAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(authService.isGuestEmailVerified("g@test.com")).willReturn(false);

        assertThatThrownBy(() -> applicationService.applyAsGuest(gatheringId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("게더링이 존재하지 않으면 예외 발생")
    void apply_gatheringNotFound_throwsException() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    @Test
    @DisplayName("모집 중이 아닌 게더링에 신청하면 예외 발생")
    void apply_gatheringNotOpen_throwsException() {
        gathering.changeStatus(GatheringStatus.CLOSED);
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_RECRUITING);
    }

    @Test
    @DisplayName("eventDate가 지난 OPEN 게더링에 신청하면 예외 발생 (KAN-163)")
    void apply_pastGathering_throwsException() {
        Gathering pastGathering = Gathering.builder()
                .title("지난 게더링")
                .eventDate(LocalDate.now().minusDays(1))
                .maxAttendees(10)
                .build();
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(pastGathering));

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_RECRUITING);
    }

    @Test
    @DisplayName("정원이 초과된 게더링에 신청하면 예외 발생")
    void apply_gatheringFull_throwsException() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any()))
                .willReturn(gathering.getMaxAttendees());

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_FULL);
    }

    @Test
    @DisplayName("회원이 이미 신청한 게더링에 재신청하면 예외 발생")
    void apply_memberAlreadyApplied_throwsException() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsByGatheringIdAndParticipant_User_IdAndDeletedAtIsNull(any(), any())).willReturn(true);

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("비회원 신청 시 전화번호가 없으면 예외 발생")
    void apply_guestPhoneMissing_throwsException() {
        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GUEST_PHONE_REQUIRED);
    }

    @Test
    @DisplayName("비회원이 이미 신청한 전화번호로 재신청하면 예외 발생")
    void apply_guestPhoneDuplicated_throwsException() {
        FormQuestion phoneQuestion = question("phone", false);
        setAnswers(request, List.of(answerItem(phoneQuestion.getId(), "01098765432")));

        given(gatheringRepository.findByIdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(gathering));
        given(applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any()))
                .willReturn(List.of(phoneQuestion));
        given(applicationRepository.existsByGatheringIdAndPhoneAndDeletedAtIsNull(any(), any())).willReturn(true);

        assertThatThrownBy(() -> applicationService.apply(gatheringId, request, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_APPLIED);
    }

    // ── cancel() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 취소")
    void cancel_success() {
        Application application = buildApplication(ApplicationStatus.PENDING, user, gathering);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        applicationService.cancel(applicationId, userId);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(eventPublisher).should().publishEvent(any(ApplicationCancelledEvent.class));
    }

    @Test
    @DisplayName("신청이 존재하지 않으면 예외 발생")
    void cancel_applicationNotFound_throwsException() {
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.cancel(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 신청이 아니면 예외 발생")
    void cancel_forbidden_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        Application application = buildApplication(ApplicationStatus.PENDING, user, gathering);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(applicationId, otherUserId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);
    }

    @Test
    @DisplayName("비회원 신청은 취소 불가")
    void cancel_guestApplication_throwsException() {
        Application application = buildApplication(ApplicationStatus.PENDING, null, gathering);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);
    }

    @Test
    @DisplayName("PENDING 상태가 아니면 취소 불가")
    void cancel_notPending_throwsException() {
        Application application = buildApplication(ApplicationStatus.CONFIRMED, user, gathering);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_CANCEL);
    }

    // ── checkApplication() ───────────────────────────────────────────────────

    @Test
    @DisplayName("예약번호와 전화번호로 신청 조회 성공")
    void checkApplication_success() {
        Application application = buildApplication(ApplicationStatus.PENDING, null, gathering);
        given(applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull("01012345678", "WH260428-ABC123"))
                .willReturn(Optional.of(application));

        ApplicationCheckResponse response = applicationService.checkApplication("01012345678", "WH260428-ABC123");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("우연한 식탁 확정 조회는 이용권 사용 후 남은 회차를 포함한다")
    void checkApplication_randomTableConfirmed_includesTicketRemainingCount() {
        Gathering randomTable = Gathering.builder()
                .title("우연한 식탁")
                .eventDate(LocalDate.now().plusDays(7))
                .maxAttendees(4)
                .gatheringType(GatheringType.RANDOM_TABLE)
                .build();
        Application application = buildApplication(ApplicationStatus.CONFIRMED, user, randomTable);
        ReflectionTestUtils.setField(application, "id", applicationId);
        TicketTransaction transaction = org.mockito.Mockito.mock(TicketTransaction.class);

        given(applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull("01012345678", "WH260428-ABC123"))
                .willReturn(Optional.of(application));
        given(ticketTransactionRepository.findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                applicationId, TicketTransactionType.USE)).willReturn(Optional.of(transaction));
        given(transaction.getBalanceAfter()).willReturn(3);

        ApplicationCheckResponse response = applicationService.checkApplication("01012345678", "WH260428-ABC123");

        assertThat(response.getTicketRemainingCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("일치하는 신청이 없으면 예외 발생")
    void checkApplication_notFound_throwsException() {
        given(applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.checkApplication("01099999999", "WH000000-XXXXXX"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    // ── getMyApplications() ──────────────────────────────────────────────────

    @Test
    @DisplayName("내 신청 목록 반환")
    void getMyApplications_returnsApplications() {
        Application application = buildApplication(ApplicationStatus.PENDING, user, gathering);
        given(applicationRepository.findByParticipant_User_IdAndDeletedAtIsNull(userId)).willReturn(List.of(application));

        List<ApplicationListResponse> result = applicationService.getMyApplications(userId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("신청 내역이 없으면 빈 리스트 반환")
    void getMyApplications_empty_returnsEmptyList() {
        given(applicationRepository.findByParticipant_User_IdAndDeletedAtIsNull(userId)).willReturn(List.of());

        List<ApplicationListResponse> result = applicationService.getMyApplications(userId);

        assertThat(result).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Application buildApplication(ApplicationStatus status, User applicationUser, Gathering applicationGathering) {
        Application application = Application.builder()
                .bookingNumber("WH260428-ABC123")
                .gathering(applicationGathering)
                .participant(applicationUser != null ? Participant.member(applicationUser) : Participant.guest("비회원", "g@test.com", "01012345678"))
                .name(applicationUser != null ? applicationUser.getName() : "비회원")
                .phone(applicationUser != null ? applicationUser.getPhone() : "01012345678")
                .build();

        if (status == ApplicationStatus.CONFIRMED) application.confirm();
        if (status == ApplicationStatus.CANCELLED) application.cancel();

        return application;
    }

    private Form activeForm() {
        return Form.builder()
                .gathering(gathering)
                .isTemplate(false)
                .build();
    }

    private FormQuestion question(String questionKey, boolean required) {
        FormQuestion question = FormQuestion.builder()
                .form(activeForm())
                .questionKey(questionKey)
                .type(QuestionType.SHORT_TEXT)
                .label(questionKey)
                .required(required)
                .displayOrder(0)
                .build();
        ReflectionTestUtils.setField(question, "id", UUID.randomUUID());
        return question;
    }

    private AnswerItem answerItem(UUID questionId, Object value) {
        AnswerItem item = new AnswerItem();
        ReflectionTestUtils.setField(item, "questionId", questionId);
        ReflectionTestUtils.setField(item, "value", value);
        return item;
    }

    private void setAnswers(ApplicationRequest req, List<AnswerItem> answers) {
        ReflectionTestUtils.setField(req, "answers", answers);
    }
}
