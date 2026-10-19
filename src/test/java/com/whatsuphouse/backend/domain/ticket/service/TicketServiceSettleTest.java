package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.application.client.service.MembershipWeekRule;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TicketServiceSettleTest {

    @Mock private TicketPassRepository ticketPassRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketTransactionRepository ticketTransactionRepository;
    @Mock private TicketProductRepository ticketProductRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private GatheringSessionService gatheringSessionService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DepositService depositService;
    @Mock private MembershipWeekRule membershipWeekRule;
    @Mock private ExceptionCaseService exceptionCaseService;

    @InjectMocks private TicketService ticketService;

    private User user;
    private UUID passId;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        passId = UUID.randomUUID();
    }

    private TicketPass pendingPass(Application application) {
        TicketPass pass = new TicketPass(user, application, new TicketProductOption("우연한 식탁 4회권", 4, 18000));
        ReflectionTestUtils.setField(pass, "id", passId);
        return pass;
    }

    private Application paymentPendingApplication(int maxAttendees) {
        Gathering gathering = Gathering.builder().title("우연한 식탁")
                .gatheringType(GatheringType.RANDOM_TABLE).build();
        ReflectionTestUtils.setField(gathering, "id", UUID.randomUUID());
        GatheringSession session = GatheringSession.builder().gathering(gathering)
                .eventDate(LocalDate.now().plusDays(7)).maxAttendees(maxAttendees).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        Application application = Application.builder().bookingNumber("WH-PAY-001")
                .session(session).user(user).name("홍길동").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.awaitPayment();
        return application;
    }

    @Test
    @DisplayName("입금 확인하면 이용권이 활성화되고 결제 대기 신청이 차감·확정되며 거래 기록과 이벤트가 남는다")
    void settlePass_activatesAndConfirmsPendingApplication() {
        Application application = paymentPendingApplication(4);
        TicketPass pass = pendingPass(application);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(gatheringSessionService.findSessionForUpdate(application.getSession().getId()))
                .willReturn(Optional.of(application.getSession()));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                application.getSession().getId(), ApplicationStatus.SEAT_OCCUPYING)).willReturn(0);
        given(depositService.isSatisfied(application.getId())).willReturn(true);

        TicketPass settled = ticketService.settlePass(passId, "WEBHOOK");

        assertThat(settled).isSameAs(pass);
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(pass.getRemainingCount()).isEqualTo(3);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(application.isPaymentConfirmed()).isTrue();
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should(times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TicketTransaction::getTransactionType)
                .containsExactly(TicketTransactionType.ISSUE, TicketTransactionType.USE);
        assertThat(saved.getAllValues()).allSatisfy(tx -> assertThat(tx.getReason()).contains("WEBHOOK"));
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
        then(gatheringSessionService).should().findSessionForUpdate(application.getSession().getId());
    }

    @Test
    @DisplayName("보증금 입금 전이면 이용권은 차감(USE)하되 신청은 결제 대기로 두고 확정 이벤트를 내지 않는다 (KAN-402)")
    void settlePass_depositUnpaid_usesTicketButStaysPending() {
        Application application = paymentPendingApplication(4);
        TicketPass pass = pendingPass(application);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(gatheringSessionService.findSessionForUpdate(application.getSession().getId()))
                .willReturn(Optional.of(application.getSession()));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                application.getSession().getId(), ApplicationStatus.SEAT_OCCUPYING)).willReturn(0);
        given(depositService.isSatisfied(application.getId())).willReturn(false);

        ticketService.settlePass(passId, "WEBHOOK");

        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(pass.getRemainingCount()).isEqualTo(3);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should(times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(TicketTransaction::getTransactionType)
                .containsExactly(TicketTransactionType.ISSUE, TicketTransactionType.USE);
        then(eventPublisher).should(never()).publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("이미 이용권을 쓰고 보증금만 기다리는 신청이면 새 이용권에서 또 차감하지 않는다 (KAN-402)")
    void settlePass_applicationAlreadyUsedTicket_noSecondDeduction() {
        Application application = paymentPendingApplication(4);
        TicketPass pass = pendingPass(application);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(ticketTransactionRepository.existsByApplication_IdAndTransactionType(
                application.getId(), TicketTransactionType.USE)).willReturn(true);

        ticketService.settlePass(passId, "WEBHOOK");

        assertThat(pass.getRemainingCount()).isEqualTo(4);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.ISSUE);
    }

    @Test
    @DisplayName("멤버십 입금 확인 시 주 1회 조건을 어기면 이용권만 활성화, 신청은 결제 대기 + 예외함 CONFLICT (KAN-402)")
    void settlePass_periodPassWeeklyLimitViolated_opensConflict() {
        Application application = periodPendingApplication();
        TicketPass pass = periodPass(application);
        LocalDate thursday = LocalDate.now().plusDays(10);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(membershipWeekRule.candidateDates(application.getId())).willReturn(List.of(thursday));
        given(membershipWeekRule.check(user.getId(), application.getId(), List.of(thursday)))
                .willReturn(Optional.of(ErrorCode.MEMBERSHIP_WEEKLY_LIMIT));

        ticketService.settlePass(passId, "WEBHOOK");

        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        then(exceptionCaseService).should().open(eq(ExceptionCaseType.CONFLICT), isNull(), isNull(), eq(application.getId()),
                startsWith("멤버십 조건 불충족: " + ErrorCode.MEMBERSHIP_WEEKLY_LIMIT.getMessage()));
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.ISSUE);
        then(eventPublisher).should(never()).publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("멤버십 기간이 마지막 희망 회차를 덮지 못하면 이용권만 활성화하고 예외함 CONFLICT (KAN-402)")
    void settlePass_periodPassNotCoveringLatestCandidate_opensConflict() {
        Application application = periodPendingApplication();
        TicketPass pass = periodPass(application);
        LocalDate farAway = LocalDate.now().plusMonths(2);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(membershipWeekRule.candidateDates(application.getId())).willReturn(List.of(farAway));

        ticketService.settlePass(passId, "WEBHOOK");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        then(exceptionCaseService).should().open(eq(ExceptionCaseType.CONFLICT), isNull(), isNull(), eq(application.getId()),
                startsWith("멤버십 조건 불충족: "));
        then(membershipWeekRule).should(never()).check(any(), any(), any());
    }

    @Test
    @DisplayName("멤버십 조건을 지키면 수량 0 USE로 확정한다")
    void settlePass_periodPassRulesMet_confirms() {
        Application application = periodPendingApplication();
        TicketPass pass = periodPass(application);
        LocalDate thursday = LocalDate.now().plusDays(10);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(membershipWeekRule.candidateDates(application.getId())).willReturn(List.of(thursday));
        given(membershipWeekRule.check(user.getId(), application.getId(), List.of(thursday))).willReturn(Optional.empty());
        given(depositService.isSatisfied(application.getId())).willReturn(true);

        ticketService.settlePass(passId, "WEBHOOK");

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(exceptionCaseService).shouldHaveNoInteractions();
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    // 매칭 전(회차 미배정) 우연한 식탁 결제 대기 신청.
    private Application periodPendingApplication() {
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        Application application = Application.builder().bookingNumber("WH-PAY-M01").gathering(gathering)
                .user(user).name("홍길동").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.awaitPayment();
        return application;
    }

    private TicketPass periodPass(Application application) {
        TicketPass pass = new TicketPass(user, application, new TicketProductOption(
                "멤버십 1개월", 0, 35000, TicketProductKind.PERIOD, 1));
        ReflectionTestUtils.setField(pass, "id", passId);
        return pass;
    }

    @Test
    @DisplayName("결제 대기 신청이 없으면 활성화와 ISSUE 거래만 남기고 전체 횟수가 충전된다")
    void settlePass_noPendingApplication_onlyIssues() {
        TicketPass pass = pendingPass(null);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(applicationRepository.findFirstByUser_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                user.getId(), ApplicationStatus.PAYMENT_PENDING)).willReturn(Optional.empty());

        ticketService.settlePass(passId, "ADMIN:" + UUID.randomUUID());

        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.ACTIVE);
        assertThat(pass.getRemainingCount()).isEqualTo(4);
        ArgumentCaptor<TicketTransaction> saved = ArgumentCaptor.forClass(TicketTransaction.class);
        then(ticketTransactionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TicketTransactionType.ISSUE);
        then(eventPublisher).should(never()).publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("이미 활성화된 이용권을 다시 정산하면 TICKET_ALREADY_PROCESSED")
    void settlePass_alreadyActive_throws() {
        TicketPass pass = pendingPass(null);
        pass.activate();
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));

        assertThatThrownBy(() -> ticketService.settlePass(passId, "WEBHOOK"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("존재하지 않는 이용권이면 TICKET_PASS_NOT_FOUND")
    void settlePass_notFound_throws() {
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.settlePass(passId, "WEBHOOK"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PASS_NOT_FOUND);
    }

    @Test
    @DisplayName("대기 신청 회차가 이미 가득 차 있으면 이용권을 활성화하지 않고 GATHERING_FULL")
    void settlePass_sessionFull_throwsBeforeActivation() {
        Application application = paymentPendingApplication(2);
        TicketPass pass = pendingPass(application);
        given(ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)).willReturn(Optional.of(pass));
        given(gatheringSessionService.findSessionForUpdate(application.getSession().getId()))
                .willReturn(Optional.of(application.getSession()));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                application.getSession().getId(), ApplicationStatus.SEAT_OCCUPYING)).willReturn(2);

        assertThatThrownBy(() -> ticketService.settlePass(passId, "WEBHOOK"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_FULL);
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.PENDING);
        then(gatheringSessionService).should().findSessionForUpdate(application.getSession().getId());
    }

    @Test
    @DisplayName("입금 대사 후보는 기한이 남은 PENDING 이용권만, 입금자명·금액·신청 ID와 함께 돌려준다")
    void listPendingPasses_excludesExpiredAndOwnerless() {
        Application application = paymentPendingApplication(4);
        TicketPass live = pendingPass(application);
        TicketPass expired = pendingPass(null);
        ReflectionTestUtils.setField(expired, "paymentDeadline", LocalDateTime.now().minusMinutes(1));
        TicketPass ownerless = new TicketPass(null, null, new TicketProductOption("레거시", 1, 9900));
        given(ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING))
                .willReturn(List.of(live, expired, ownerless));

        List<PendingPayment> result = ticketService.listPendingPasses();

        assertThat(result).containsExactly(new PendingPayment(
                PaymentTargetType.TICKET_PASS, passId, application.getId(), "홍길동", 18000));
        then(ticketTransactionRepository).should(never()).save(any());
    }
}
