package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
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
    @Mock private ApplicationEventPublisher eventPublisher;

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
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(passId)).willReturn(Optional.of(pass));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                application.getSession().getId(), ApplicationStatus.SEAT_OCCUPYING)).willReturn(0);

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
    }

    @Test
    @DisplayName("결제 대기 신청이 없으면 활성화와 ISSUE 거래만 남기고 전체 횟수가 충전된다")
    void settlePass_noPendingApplication_onlyIssues() {
        TicketPass pass = pendingPass(null);
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(passId)).willReturn(Optional.of(pass));
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
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(passId)).willReturn(Optional.of(pass));

        assertThatThrownBy(() -> ticketService.settlePass(passId, "WEBHOOK"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("존재하지 않는 이용권이면 TICKET_PASS_NOT_FOUND")
    void settlePass_notFound_throws() {
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(passId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.settlePass(passId, "WEBHOOK"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_PASS_NOT_FOUND);
    }

    @Test
    @DisplayName("대기 신청 회차가 이미 가득 차 있으면 이용권을 활성화하지 않고 GATHERING_FULL")
    void settlePass_sessionFull_throwsBeforeActivation() {
        Application application = paymentPendingApplication(2);
        TicketPass pass = pendingPass(application);
        given(ticketPassRepository.findByIdAndDeletedAtIsNull(passId)).willReturn(Optional.of(pass));
        given(applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                application.getSession().getId(), ApplicationStatus.SEAT_OCCUPYING)).willReturn(2);

        assertThatThrownBy(() -> ticketService.settlePass(passId, "WEBHOOK"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GATHERING_FULL);
        assertThat(pass.getStatus()).isEqualTo(TicketPassStatus.PENDING);
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
