package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock private DepositRepository depositRepository;
    @Mock private ObjectProvider<TicketService> ticketServiceProvider;
    @Mock private TicketService ticketService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PayoutService payoutService;

    @InjectMocks
    private DepositService depositService;

    private Application application;

    @BeforeEach
    void setUp() {
        User user = User.builder().email("t@example.com").password("p").name("김철수").gender(Gender.MALE)
                .age(28).nickname("chulsoo").phone("01012345678").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        Gathering randomTable = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        application = Application.builder().bookingNumber("WH261019-DEP001").gathering(randomTable)
                .user(user).name("김철수").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.awaitPayment();
    }

    @Test
    @DisplayName("신청 시 보증금은 50,000원 PENDING으로 반환 계좌와 함께 만든다")
    void createPending_savesPending50000() {
        given(depositRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        RefundAccount account = new RefundAccount("국민", "12345678901", "김철수");

        Deposit deposit = depositService.createPending(application, account);

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PENDING);
        assertThat(deposit.getAmount()).isEqualTo(50_000);
        assertThat(deposit.getReturnAccount()).isSameAs(account);
        assertThat(deposit.getApplication()).isSameAs(application);
    }

    @Test
    @DisplayName("보증금 입금 확인 후 이용권 사용(USE)도 있으면 결제 대기 신청을 확정하고 확정 이벤트를 낸다")
    void markPaid_thenConfirmsApplication() {
        Deposit deposit = pendingLocked();
        given(ticketServiceProvider.getObject()).willReturn(ticketService);
        given(ticketService.hasTicketUse(application.getId())).willReturn(true);

        boolean marked = depositService.markPaid(application.getId());

        assertThat(marked).isTrue();
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PAID);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CONFIRMED);
        then(eventPublisher).should().publishEvent(any(ApplicationConfirmedEvent.class));
    }

    @Test
    @DisplayName("보증금은 입금됐지만 이용권을 아직 안 썼으면(이용권 입금 대기) 결제 대기로 둔다")
    void markPaid_withoutTicketUse_staysPaymentPending() {
        Deposit deposit = pendingLocked();
        given(ticketServiceProvider.getObject()).willReturn(ticketService);
        given(ticketService.hasTicketUse(application.getId())).willReturn(false);

        depositService.markPaid(application.getId());

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PAID);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PAYMENT_PENDING);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 처리된 보증금을 다시 입금 확인해도 아무것도 바꾸지 않고 false(입금 대사는 중복 입금으로 본다)")
    void markPaid_alreadyPaid_noop() {
        Deposit deposit = pendingLocked();
        deposit.markPaid();

        boolean marked = depositService.markPaid(application.getId());

        assertThat(marked).isFalse();
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PAID);

        then(ticketServiceProvider).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 취소된 신청의 보증금이 입금되면 확정하지 않고 바로 반환 대기로 둔다")
    void markPaid_cancelledApplication_returnPending() {
        Deposit deposit = pendingLocked();
        application.cancel();

        depositService.markPaid(application.getId());

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.RETURN_PENDING);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        then(payoutService).should().request(PayoutType.DEPOSIT_RETURN, application.getUser().getId(), 50_000,
                deposit.getReturnAccount(), application.getId(), null, deposit.getId());
        then(ticketServiceProvider).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("isSatisfied: 보증금이 없거나 PAID 이상이면 true, PENDING이면 false")
    void isSatisfied() {
        UUID noDeposit = UUID.randomUUID();
        given(depositRepository.findByApplication_Id(noDeposit)).willReturn(Optional.empty());
        Deposit deposit = pending();

        assertThat(depositService.isSatisfied(noDeposit)).isTrue();
        assertThat(depositService.isSatisfied(application.getId())).isFalse();
        deposit.markPaid();
        assertThat(depositService.isSatisfied(application.getId())).isTrue();
        deposit.forfeit();
        assertThat(depositService.isSatisfied(application.getId())).isTrue();
    }

    @Test
    @DisplayName("반환 요청: 잠근 뒤 최신 상태로 입금된(PAID) 보증금만 RETURN_PENDING + 반환 계좌로 송금 요청 1건, 다시 불러도 그대로")
    void requestReturn_paidOnly_createsPayoutOnce() {
        Deposit deposit = pendingLocked();

        depositService.requestReturn(application.getId());
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PENDING);

        deposit.markPaid();
        depositService.requestReturn(application.getId());
        depositService.requestReturn(application.getId());

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.RETURN_PENDING);
        then(depositRepository).should(times(3)).refreshForUpdate(deposit);
        then(payoutService).should(times(1)).request(PayoutType.DEPOSIT_RETURN, application.getUser().getId(), 50_000,
                deposit.getReturnAccount(), application.getId(), null, deposit.getId());
    }

    @Test
    @DisplayName("부분 반환(50%): RETURN_PENDING + 반환 금액 25,000원으로 송금 요청 (KAN-418)")
    void requestReturn_halfPercent_partialPayout() {
        Deposit deposit = pendingLocked();
        deposit.markPaid();

        depositService.requestReturn(application.getId(), 50);

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.RETURN_PENDING);
        assertThat(deposit.getReturnAmount()).isEqualTo(25_000);
        then(payoutService).should().request(PayoutType.DEPOSIT_RETURN, application.getUser().getId(), 25_000,
                deposit.getReturnAccount(), application.getId(), null, deposit.getId());
    }

    @Test
    @DisplayName("0% 반환은 몰수: FORFEITED, 0원 송금 요청을 만들지 않는다 (KAN-418)")
    void requestReturn_zeroPercent_forfeitsWithoutPayout() {
        Deposit deposit = pendingLocked();
        deposit.markPaid();

        depositService.requestReturn(application.getId(), 0);

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.FORFEITED);
        assertThat(deposit.getReturnAmount()).isNull();
        then(payoutService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("몰수는 아직 돌려주지 않은 보증금(PAID·RETURN_PENDING)만 FORFEITED로, 반환 대기였으면 송금 전 반환 요청을 거둔다")
    void forfeit_paidOrReturnPending() {
        Deposit deposit = pendingLocked();
        depositService.forfeit(application.getId());
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PENDING);

        deposit.markPaid();
        deposit.requestReturn();
        depositService.forfeit(application.getId());
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.FORFEITED);
        then(payoutService).should().withdrawDepositReturn(deposit.getId());
    }

    @Test
    @DisplayName("송금 완료 후속: 반환 대기 보증금만 RETURNED로")
    void markReturned_onlyReturnPending() {
        Deposit deposit = Deposit.pending(application, null);
        UUID depositId = UUID.randomUUID();
        ReflectionTestUtils.setField(deposit, "id", depositId);
        given(depositRepository.findById(depositId)).willReturn(Optional.of(deposit));

        depositService.markReturned(depositId);
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PENDING);

        deposit.markPaid();
        deposit.requestReturn();
        depositService.markReturned(depositId);
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.RETURNED);
    }

    @Test
    @DisplayName("입금 대사 후보: 입금 대기 보증금을 DEPOSIT 50,000원, 회원 이름으로 낸다")
    void listPendingStandalone() {
        Deposit deposit = Deposit.pending(application, null);
        ReflectionTestUtils.setField(deposit, "id", UUID.randomUUID());
        given(depositRepository.findByStatusAndApplication_DeletedAtIsNullOrderByCreatedAtAsc(DepositStatus.PENDING))
                .willReturn(List.of(deposit));

        assertThat(depositService.listPendingStandalone()).containsExactly(new PendingPayment(
                PaymentTargetType.DEPOSIT, deposit.getId(), application.getId(), "김철수", 50_000));
    }

    @Test
    @DisplayName("미충족 신청 ID: 보증금 PENDING인 신청만 돌려준다")
    void findUnsatisfiedApplicationIds() {
        Deposit unpaid = Deposit.pending(application, null);
        Application other = Application.builder().bookingNumber("WH261019-DEP002")
                .gathering(application.getGathering()).user(application.getUser()).name("김철수").phone("01012345678").build();
        ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
        Deposit paid = Deposit.pending(other, null);
        paid.markPaid();
        List<UUID> ids = List.of(application.getId(), other.getId(), UUID.randomUUID());
        given(depositRepository.findByApplication_IdIn(ids)).willReturn(List.of(unpaid, paid));

        assertThat(depositService.findUnsatisfiedApplicationIds(ids)).containsExactly(application.getId());
    }

    @Test
    @DisplayName("findPendingByApplication: PENDING 보증금만 돌려준다")
    void findPendingByApplication() {
        Deposit deposit = pending();
        assertThat(depositService.findPendingByApplication(application.getId())).contains(deposit);
        deposit.markPaid();
        assertThat(depositService.findPendingByApplication(application.getId())).isEmpty();
    }

    // markPaid는 보증금 행을 잠가 읽는다.
    private Deposit pendingLocked() {
        Deposit deposit = Deposit.pending(application, new RefundAccount("국민", "12345678901", "김철수"));
        given(depositRepository.findByApplication_IdForUpdate(application.getId())).willReturn(Optional.of(deposit));
        return deposit;
    }

    private Deposit pending() {
        Deposit deposit = Deposit.pending(application, new RefundAccount("국민", "12345678901", "김철수"));
        given(depositRepository.findByApplication_Id(application.getId())).willReturn(Optional.of(deposit));
        return deposit;
    }
}
