package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.AnswerItem;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationCreateRequest;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationLookupTokenService;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.auth.event.GuestEmailVerificationConsumedEvent;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.form.repository.FormQuestionRepository;
import com.whatsuphouse.backend.domain.form.repository.FormRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.whatsuphouse.backend.domain.notification.event.ApplicationApprovedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPendingEvent;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;

    @Mock
    private GatheringSessionRepository gatheringSessionRepository;

    @Mock
    private UserRepository userRepository;

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
    private UUID sessionId;
    private UUID userId;
    private UUID applicationId;
    private Gathering gathering;
    private GatheringSession session;
    private User user;
    private ApplicationRequest request;

    @BeforeEach
    void setUp() {
        gatheringId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        gathering = Gathering.builder()
                .title("재즈 게더링")
                .build();
        session = sessionOf(gathering, LocalDate.now().plusDays(7), 10);

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
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsBySession_IdAndUser_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(sessionId, request, userId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.getGatheringId()).isEqualTo(sessionId);
        then(applicationCandidateSessionRepository).should().save(argThat(candidate ->
                candidate.getSession() == session && candidate.getPriority() == 1));
        then(eventPublisher).should().publishEvent(any(ApplicationPendingEvent.class));
    }

    @Test
    @DisplayName("승인된 우연한 식탁 회원은 이용권 차감 후 자동 확정된다 (KAN-277)")
    void apply_randomTable_approvedMember_autoConfirmsWithTicket() {
        Gathering randomTable = Gathering.builder().title("우연한 식탁").build();
        GatheringSession randomTableSession = sessionOf(randomTable, LocalDate.now().plusDays(7), 4);
        ReflectionTestUtils.setField(randomTable, "gatheringType", GatheringType.RANDOM_TABLE);

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(randomTableSession));
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(any(), any())).willReturn(false);
        user.approveRandomTable();
        given(ticketService.tryUseOneTicket(eq(user), any(Application.class))).willReturn(true);
        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        given(applicationRepository.save(saved.capture())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(sessionId, request, userId);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        // 차감 성공 → 매칭 대기 (KAN-342)
        assertThat(saved.getValue().getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        then(ticketService).should().tryUseOneTicket(eq(user), any(Application.class));
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("승인된 우연한 식탁 회원에게 이용권이 없으면 재심사 없이 결제 대기한다 (KAN-277)")
    void apply_randomTable_approvedMemberWithoutTicket_awaitsPayment() {
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession randomTableSession = sessionOf(randomTable, LocalDate.now().plusDays(7), 4);
        user.approveRandomTable();

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(randomTableSession));
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(any(), any())).willReturn(false);
        given(ticketService.tryUseOneTicket(eq(user), any(Application.class))).willReturn(false);
        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        given(applicationRepository.save(saved.capture())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(sessionId, request, userId);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        // 결제 대기여도 매칭 상태는 WAITING. 엔진은 CONFIRMED만 후보로 보므로 섞이지 않는다.
        // 결제 대기 알림(DINING_PAYMENT_PENDING)은 이 이벤트로 나간다. (KAN-342)
        assertThat(saved.getValue().getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        then(eventPublisher).should().publishEvent(any(ApplicationApprovedEvent.class));
    }

    @Test
    @DisplayName("무료 우연한 식탁은 승인된 회원 신청 시 이용권 차감 없이 자동 확정된다")
    void apply_freeRandomTable_approvedMember_confirmsWithoutTicket() {
        Gathering randomTable = Gathering.builder().title("무료 우연한 식탁").basePrice(0).gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession randomTableSession = sessionOf(randomTable, LocalDate.now().plusDays(7), 4);
        user.approveRandomTable();

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(randomTableSession));
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(any(), any())).willReturn(false);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(sessionId, request, userId);

        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(ticketService).should(never()).tryUseOneTicket(eq(user), any(Application.class));
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("거절된 참가자는 우연한 식탁을 다시 신청할 수 없다 (KAN-277)")
    void apply_randomTable_rejectedMember_isBlocked() {
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession randomTableSession = sessionOf(randomTable, LocalDate.now().plusDays(7), 4);
        user.rejectRandomTable();

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(randomTableSession));
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(any(), any())).willReturn(false);

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
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

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any()))
                .willReturn(List.of(phoneQuestion, emailQuestion));
        given(applicationRepository.existsBySession_IdAndPhoneAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(authService.isGuestEmailVerified("g@test.com")).willReturn(true);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.apply(sessionId, request, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        then(eventPublisher).should().publishEvent(any(ApplicationPendingEvent.class));
        then(eventPublisher).should().publishEvent(any(GuestEmailVerificationConsumedEvent.class));
    }

    @Test
    @DisplayName("비회원 이메일이 인증되지 않으면 신청을 차단한다")
    void apply_guestEmailNotVerified_throws() {
        FormQuestion phoneQuestion = question("phone", false);
        FormQuestion emailQuestion = question("email", false);
        setAnswers(request, List.of(
                answerItem(phoneQuestion.getId(), "01098765432"),
                answerItem(emailQuestion.getId(), "g@test.com")));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any()))
                .willReturn(List.of(phoneQuestion, emailQuestion));
        given(applicationRepository.existsBySession_IdAndPhoneAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(authService.isGuestEmailVerified("g@test.com")).willReturn(false);

        assertThatThrownBy(() -> applicationService.applyAsGuest(sessionId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("게더링이 존재하지 않으면 예외 발생")
    void apply_gatheringNotFound_throwsException() {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_FOUND);
    }

    @Test
    @DisplayName("모집 중이 아닌 게더링에 신청하면 예외 발생")
    void apply_gatheringNotOpen_throwsException() {
        session.changeStatus(GatheringSessionStatus.CLOSED);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_RECRUITING);
    }

    @Test
    @DisplayName("eventDate가 지난 OPEN 게더링에 신청하면 예외 발생 (KAN-163)")
    void apply_pastGathering_throwsException() {
        Gathering pastGathering = Gathering.builder().title("지난 게더링").build();
        GatheringSession pastGatheringSession = sessionOf(pastGathering, LocalDate.now().minusDays(1), 10);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(pastGatheringSession));

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_NOT_RECRUITING);
    }

    @Test
    @DisplayName("정원이 초과된 게더링에 신청하면 예외 발생")
    void apply_gatheringFull_throwsException() {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any()))
                .willReturn(session.getMaxAttendees());

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_FULL);
    }

    @Test
    @DisplayName("회원이 이미 신청한 게더링에 재신청하면 예외 발생")
    void apply_memberAlreadyApplied_throwsException() {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsBySession_IdAndUser_IdAndDeletedAtIsNull(any(), any())).willReturn(true);

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("비회원 신청 시 전화번호가 없으면 예외 발생")
    void apply_guestPhoneMissing_throwsException() {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GUEST_PHONE_REQUIRED);
    }

    @Test
    @DisplayName("비회원이 이미 신청한 전화번호로 재신청하면 예외 발생")
    void apply_guestPhoneDuplicated_throwsException() {
        FormQuestion phoneQuestion = question("phone", false);
        setAnswers(request, List.of(answerItem(phoneQuestion.getId(), "01098765432")));

        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId))
                .willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any()))
                .willReturn(List.of(phoneQuestion));
        given(applicationRepository.existsBySession_IdAndPhoneAndDeletedAtIsNull(any(), any())).willReturn(true);

        assertThatThrownBy(() -> applicationService.apply(sessionId, request, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_APPLIED);
    }

    // ── apply(ApplicationCreateRequest) — 종류 + 희망 회차 (KAN-338) ─────────

    @Test
    @DisplayName("일반 모임에 회차를 2개 고르면 SINGLE_SESSION_REQUIRED")
    void applyWithCandidates_regularWithTwoSessions_throwsException() {
        // given
        GatheringSession other = otherSessionOf(gathering);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(other.getId())).willReturn(Optional.of(other));

        // when & then
        assertThatThrownBy(() -> applicationService.apply(createRequest(sessionId, other.getId()), userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SINGLE_SESSION_REQUIRED);
    }

    @Test
    @DisplayName("우연한 식탁은 희망 회차를 요청 순서대로 저장하고, 배정 회차 없이 매칭 대기(WAITING)로 접수한다")
    void applyWithCandidates_randomTable_savesCandidatesInOrderAndWaits() {
        // given
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession first = sessionOf(randomTable, LocalDate.now().plusDays(7), 6);
        GatheringSession second = otherSessionOf(randomTable);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(first.getId())).willReturn(Optional.of(first));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(second.getId())).willReturn(Optional.of(second));
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
                List.of(second.getId(), first.getId()), userId)).willReturn(false);
        ArgumentCaptor<Application> savedApplication = ArgumentCaptor.forClass(Application.class);
        given(applicationRepository.save(savedApplication.capture())).willAnswer(inv -> inv.getArgument(0));

        // when: 두 번째로 만든 회차를 1순위로 고른다
        ApplicationResponse response = applicationService.apply(createRequest(second.getId(), first.getId()), userId);

        // then
        Application application = savedApplication.getValue();
        assertThat(application.getSession()).isNull();
        assertThat(application.getGathering()).isSameAs(randomTable);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.getMatchStatus()).isEqualTo(MatchStatus.WAITING);
        assertThat(response.getGatheringId()).isEqualTo(gatheringId);
        ArgumentCaptor<ApplicationCandidateSession> candidates = ArgumentCaptor.forClass(ApplicationCandidateSession.class);
        then(applicationCandidateSessionRepository).should(times(2)).save(candidates.capture());
        assertThat(candidates.getAllValues()).extracting(ApplicationCandidateSession::getSession)
                .containsExactly(second, first);
        assertThat(candidates.getAllValues()).extracting(ApplicationCandidateSession::getPriority)
                .containsExactly(1, 2);
        then(applicationRepository).should(never()).countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("우연한 식탁 희망 회차 중 하나라도 이미 신청했으면 ALREADY_APPLIED")
    void applyWithCandidates_randomTableAlreadyAppliedToCandidate_throwsException() {
        // given
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession randomTableSession = sessionOf(randomTable, LocalDate.now().plusDays(7), 6);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(randomTableSession));
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
                List.of(sessionId), userId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> applicationService.apply(createRequest(sessionId), userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("신청 마감이 지난 회차에 신청하면 APPLY_DEADLINE_PASSED")
    void applyWithCandidates_deadlinePassed_throwsException() {
        // given
        ReflectionTestUtils.setField(session, "applyDeadlineAt", LocalDateTime.now().minusMinutes(1));
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> applicationService.apply(createRequest(sessionId), userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLY_DEADLINE_PASSED);
    }

    @Test
    @DisplayName("다른 종류의 회차를 고르면 SESSION_GATHERING_MISMATCH(400)")
    void applyWithCandidates_sessionOfOtherGathering_throwsException() {
        // given
        Gathering otherKind = Gathering.builder().title("다른 모임").build();
        ReflectionTestUtils.setField(otherKind, "id", UUID.randomUUID());
        GatheringSession foreign = otherSessionOf(otherKind);
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(foreign.getId())).willReturn(Optional.of(foreign));

        // when & then
        assertThatThrownBy(() -> applicationService.apply(createRequest(foreign.getId()), userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_GATHERING_MISMATCH);
    }

    // ── cancel() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 취소")
    void cancel_success() {
        Application application = buildApplication(ApplicationStatus.PENDING, user, session);
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
        Application application = buildApplication(ApplicationStatus.PENDING, user, session);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(applicationId, otherUserId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);
    }

    @Test
    @DisplayName("비회원 신청은 취소 불가")
    void cancel_guestApplication_throwsException() {
        Application application = buildApplication(ApplicationStatus.PENDING, null, session);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_FORBIDDEN);
    }

    @Test
    @DisplayName("PENDING 상태가 아니면 취소 불가")
    void cancel_notPending_throwsException() {
        Application application = buildApplication(ApplicationStatus.CONFIRMED, user, session);
        given(applicationRepository.findByIdAndDeletedAtIsNull(applicationId)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancel(applicationId, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_CANCEL);
    }

    // ── checkApplication() ───────────────────────────────────────────────────

    @Test
    @DisplayName("예약번호와 전화번호로 신청 조회 성공")
    void checkApplication_success() {
        Application application = buildApplication(ApplicationStatus.PENDING, null, session);
        given(applicationRepository.findByPhoneAndBookingNumberAndDeletedAtIsNull("01012345678", "WH260428-ABC123"))
                .willReturn(Optional.of(application));

        ApplicationCheckResponse response = applicationService.checkApplication("01012345678", "WH260428-ABC123");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("우연한 식탁 확정 조회는 이용권 사용 후 남은 회차를 포함한다")
    void checkApplication_randomTableConfirmed_includesTicketRemainingCount() {
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        GatheringSession randomTableSession = sessionOf(randomTable, LocalDate.now().plusDays(7), 4);
        Application application = buildApplication(ApplicationStatus.CONFIRMED, user, randomTableSession);
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
        Application application = buildApplication(ApplicationStatus.PENDING, user, session);
        given(applicationRepository.findByUser_IdAndDeletedAtIsNull(userId)).willReturn(List.of(application));

        List<ApplicationListResponse> result = applicationService.getMyApplications(userId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("신청 내역이 없으면 빈 리스트 반환")
    void getMyApplications_empty_returnsEmptyList() {
        given(applicationRepository.findByUser_IdAndDeletedAtIsNull(userId)).willReturn(List.of());

        List<ApplicationListResponse> result = applicationService.getMyApplications(userId);

        assertThat(result).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Application buildApplication(ApplicationStatus status, User applicationUser, GatheringSession applicationSession) {
        Application application = Application.builder()
                .bookingNumber("WH260428-ABC123")
                .session(applicationSession)
                .user(applicationUser)
                .name(applicationUser != null ? applicationUser.getName() : "비회원")
                .phone(applicationUser != null ? applicationUser.getPhone() : "01012345678")
                .build();

        if (status == ApplicationStatus.CONFIRMED) application.confirm();
        if (status == ApplicationStatus.CANCELLED) application.cancel();

        return application;
    }

    // 종류 ID = gatheringId, 회차 ID = sessionId. 경로로 들어오는 ID는 회차 ID다. (KAN-337)
    private GatheringSession sessionOf(Gathering kind, LocalDate eventDate, int maxAttendees) {
        ReflectionTestUtils.setField(kind, "id", gatheringId);
        GatheringSession created = GatheringSession.builder()
                .gathering(kind)
                .eventDate(eventDate)
                .maxAttendees(maxAttendees)
                .build();
        ReflectionTestUtils.setField(created, "id", sessionId);
        return created;
    }

    // 같은 종류의 다른 회차(다음 주). ID는 새로 만든다.
    private GatheringSession otherSessionOf(Gathering kind) {
        GatheringSession created = GatheringSession.builder()
                .gathering(kind)
                .eventDate(LocalDate.now().plusDays(14))
                .maxAttendees(6)
                .build();
        ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
        return created;
    }

    // 요청 종류 ID는 setUp의 gatheringId.
    private ApplicationCreateRequest createRequest(UUID... candidateSessionIds) {
        ApplicationCreateRequest created = new ApplicationCreateRequest();
        ReflectionTestUtils.setField(created, "gatheringId", gatheringId);
        ReflectionTestUtils.setField(created, "candidateSessionIds", List.of(candidateSessionIds));
        ReflectionTestUtils.setField(created, "answers", List.of());
        return created;
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

    // ── 출생연도 검증 (KAN-388) ────────────────────────────────────────────────

    private FormQuestion birthYearQuestion() {
        FormQuestion question = question("birth_year", true);
        ReflectionTestUtils.setField(question, "type", QuestionType.NUMBER);
        ReflectionTestUtils.setField(question, "reservedKey", ReservedQuestionKey.BIRTH_YEAR);
        return question;
    }

    private void givenOpenSessionWithQuestions(List<FormQuestion> questions) {
        given(gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)).willReturn(Optional.of(session));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(any(), any())).willReturn(0);
        given(formRepository.findByGathering_IdAndDeletedAtIsNull(gatheringId)).willReturn(Optional.of(activeForm()));
        given(formQuestionRepository.findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(any())).willReturn(questions);
    }

    @Test
    @DisplayName("출생연도 답이 1900~올해(한국 시간) 정수 연도가 아니면(나이·소수·문자·빈 값·미래 연도) INVALID_BIRTH_YEAR")
    void apply_invalidBirthYear_throwsException() {
        FormQuestion birthYear = birthYearQuestion();
        givenOpenSessionWithQuestions(List.of(birthYear));
        int nextYear = Year.now(ZoneId.of("Asia/Seoul")).getValue() + 1;

        for (Object value : List.of(29, "29", 1995.5, "1995년", " ", 1899, nextYear)) {
            setAnswers(request, List.of(answerItem(birthYear.getId(), value)));
            assertThatThrownBy(() -> applicationService.apply(sessionId, request, userId))
                    .as("value=%s", value)
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_BIRTH_YEAR);
        }
        then(applicationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("출생연도는 숫자와 숫자 문자열(매칭 엔진이 읽는 형태) 모두 받는다")
    void apply_validBirthYear_numberOrNumericString_succeeds() {
        FormQuestion birthYear = birthYearQuestion();
        givenOpenSessionWithQuestions(List.of(birthYear));
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(applicationRepository.existsBySession_IdAndUser_IdAndDeletedAtIsNull(any(), any())).willReturn(false);
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        int thisYear = Year.now(ZoneId.of("Asia/Seoul")).getValue();

        for (Object value : List.of(1995, 1995.0, " 1995 ", 1900, String.valueOf(thisYear))) {
            setAnswers(request, List.of(answerItem(birthYear.getId(), value)));
            assertThat(applicationService.apply(sessionId, request, userId).getStatus())
                    .as("value=%s", value)
                    .isEqualTo(ApplicationStatus.PENDING);
        }
    }
}
