package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationCreateRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationLookupTokenService;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationApprovedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPaymentConfirmedEvent;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
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
import java.time.ZoneId;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceAutoApproveTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    @Mock private GatheringSessionService gatheringSessionService;
    @Mock private UserService userService;
    @Mock private AuthService authService;
    @Mock private FormProvisionService formProvisionService;
    @Mock private ApplicationAnswerRepository applicationAnswerRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TicketService ticketService;
    @Mock private ApplicationLookupTokenService applicationLookupTokenService;

    @InjectMocks private ApplicationService applicationService;

    private final UUID gatheringId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Gathering randomTable;
    private GatheringSession session;
    private User user;

    @BeforeEach
    void setUp() {
        randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(randomTable, "id", gatheringId);
        session = GatheringSession.builder().gathering(randomTable)
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(4).build();
        ReflectionTestUtils.setField(session, "id", sessionId);
        user = User.builder()
                .email("test@example.com").password("encoded").name("김철수")
                .gender(Gender.MALE).age(28).nickname("chulsoo").phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    private ApplicationCreateRequest request() {
        ApplicationCreateRequest created = new ApplicationCreateRequest();
        ReflectionTestUtils.setField(created, "gatheringId", gatheringId);
        ReflectionTestUtils.setField(created, "candidateSessionIds", List.of(sessionId));
        ReflectionTestUtils.setField(created, "answers", List.of());
        return created;
    }

    private void stubFormAndUser() {
        given(gatheringSessionService.findSessionForUpdate(sessionId)).willReturn(Optional.of(session));
        given(formProvisionService.findOrCreateForm(any()))
                .willReturn(Form.builder().gathering(randomTable).isTemplate(false).build());
        given(formProvisionService.findQuestions(any())).willReturn(List.of());
        given(userService.findUser(userId)).willReturn(user);
        given(applicationCandidateSessionRepository
                .existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(any(), eq(userId)))
                .willReturn(false);
    }

    @Test
    @DisplayName("심사 전(UNREVIEWED) 회원이 신청하면 자동 승인되고, 이용권이 없으면 결제 대기가 된다")
    void apply_randomTable_unreviewedUser_autoApproved() {
        stubFormAndUser();
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(ticketService.tryUseOneTicket(eq(user), any(Application.class), any())).willReturn(false);
        assertThat(user.isApprovedForRandomTable()).isFalse();

        ApplicationResponse response = applicationService.apply(request(), userId);

        assertThat(user.isApprovedForRandomTable()).isTrue();
        assertThat(response.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        then(eventPublisher).should().publishEvent(any(ApplicationApprovedEvent.class));
    }

    @Test
    @DisplayName("체험 계정의 임시 전화번호가 신청서 phone에 그대로 복사된다 (null이면 NOT NULL 위반, KAN-407)")
    void apply_randomTable_demoUserPhone_copiedToApplication() {
        user.markDemo();
        ReflectionTestUtils.setField(user, "phone", "01000001234");
        ReflectionTestUtils.setField(randomTable, "hidden", true); // 체험 계정은 심사용(숨김) 모임만 신청 가능
        stubFormAndUser();
        given(applicationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        applicationService.apply(request(), userId);

        ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
        then(applicationRepository).should().save(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo("01000001234");
    }

    @Test
    @DisplayName("제한(RESTRICTED) 회원은 신청이 거부되고 자동 승인되지 않는다")
    void apply_randomTable_restrictedUser_throws() {
        user.restrictRandomTable();
        stubFormAndUser();

        assertThatThrownBy(() -> applicationService.apply(request(), userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RANDOM_TABLE_ELIGIBILITY_RESTRICTED);
        assertThat(user.isApprovedForRandomTable()).isFalse();
        then(applicationRepository).should(never()).save(any());
    }

    // ── 시스템 입금 확인 / 대사 후보 ───────────────────────────────────────────

    private Application regularApplication(int price) {
        Gathering regular = Gathering.builder().title("재즈 게더링").basePrice(price).build();
        ReflectionTestUtils.setField(regular, "id", UUID.randomUUID());
        GatheringSession regularSession = GatheringSession.builder().gathering(regular)
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(10).build();
        ReflectionTestUtils.setField(regularSession, "id", UUID.randomUUID());
        Application application = Application.builder().bookingNumber("WH-REG-001")
                .session(regularSession).name("박영희").phone("01099998888").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        return application;
    }

    @Test
    @DisplayName("시스템 입금 확인은 처음엔 입금 완료 이벤트를 내고 true, 재확인은 아무것도 바꾸지 않고 false(중복 정산 방지)")
    void confirmRegularPaymentBySystem_firstTimeOnlyPublishes() {
        Application application = regularApplication(15000);
        given(applicationRepository.findByIdAndDeletedAtIsNullForUpdate(application.getId()))
                .willReturn(Optional.of(application));

        boolean first = applicationService.confirmRegularPaymentBySystem(application.getId());
        var confirmedAt = application.getPaymentConfirmedAt();
        boolean second = applicationService.confirmRegularPaymentBySystem(application.getId());

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(application.getPaymentConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(application.isPaymentConfirmed()).isTrue();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        then(eventPublisher).should(times(1)).publishEvent(any(ApplicationPaymentConfirmedEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 신청의 시스템 입금 확인은 APPLICATION_NOT_FOUND")
    void confirmRegularPaymentBySystem_notFound_throws() {
        UUID id = UUID.randomUUID();
        given(applicationRepository.findByIdAndDeletedAtIsNullForUpdate(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.confirmRegularPaymentBySystem(id))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("일반 모임 대사 후보는 유료 신청만 입금자명·회차 가격으로 돌려준다")
    void listPendingRegularPayments_onlyPaidGatherings() {
        Application paid = regularApplication(15000);
        Application free = regularApplication(0);
        given(applicationRepository.findUnpaidRegularApplications(eq(GatheringType.RANDOM_TABLE), any(),
                eq(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1))))
                .willReturn(List.of(paid, free));

        List<PendingPayment> result = applicationService.listPendingRegularPayments();

        assertThat(result).containsExactly(new PendingPayment(
                PaymentTargetType.REGULAR_APPLICATION, paid.getId(), paid.getId(), "박영희", 15000));
    }
}
