package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.payment.entity.PayoutRequest;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.event.PayoutSentEvent;
import com.whatsuphouse.backend.domain.payment.repository.PayoutRequestRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/** 환불·보증금 반환 송금 요청(KAN-404): 생성, 데모 즉시 송금, 운영자 송금 완료와 후속 상태, 계좌 마스킹. */
@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock private PayoutRequestRepository payoutRequestRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PayoutService payoutService;

    private final RefundAccount account = new RefundAccount("국민", "12345678901", "김철수");
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("t@example.com").password("p").name("김철수").gender(Gender.MALE)
                .age(28).nickname("chulsoo").phone("01012345678").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    private void givenSave() {
        given(payoutRequestRepository.save(any())).willAnswer(inv -> {
            PayoutRequest payout = inv.getArgument(0);
            ReflectionTestUtils.setField(payout, "id", UUID.randomUUID());
            return payout;
        });
    }

    private PayoutRequest locked(PayoutType type, UUID applicationId, UUID depositId) {
        PayoutRequest payout = PayoutRequest.request(user, type, 9_900, account, applicationId, null, depositId);
        ReflectionTestUtils.setField(payout, "id", UUID.randomUUID());
        given(payoutRequestRepository.findByIdForUpdate(payout.getId())).willReturn(Optional.of(payout));
        return payout;
    }

    @Test
    @DisplayName("일반 회원 송금 요청은 계좌와 함께 REQUESTED로 저장하고 송금 완료 이벤트는 내지 않는다")
    void request_savesRequested() {
        given(userService.findUsersByIds(List.of(user.getId()))).willReturn(Map.of(user.getId(), user));
        givenSave();
        UUID applicationId = UUID.randomUUID();

        PayoutRequest payout = payoutService.request(PayoutType.REFUND, user.getId(), 9_900, account, applicationId, null, null);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout.getAmount()).isEqualTo(9_900);
        assertThat(payout.getAccount()).isSameAs(account);
        assertThat(payout.getApplicationId()).isEqualTo(applicationId);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("데모 회원 송금 요청은 바로 SENT로 바꾸고 송금 완료 이벤트를 낸다")
    void request_demoUser_autoSent() {
        user.markDemo();
        given(userService.findUsersByIds(List.of(user.getId()))).willReturn(Map.of(user.getId(), user));
        givenSave();
        UUID applicationId = UUID.randomUUID();

        PayoutRequest payout = payoutService.request(PayoutType.REFUND, user.getId(), 9_900, account, applicationId, null, null);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SENT);
        assertThat(payout.getSentAt()).isNotNull();
        assertThat(payout.getSentBy()).isNull();
        then(eventPublisher).should().publishEvent(
                new PayoutSentEvent(payout.getId(), PayoutType.REFUND, applicationId, null));
    }

    @Test
    @DisplayName("환불 송금 요청에 계좌가 비어 있으면 REFUND_ACCOUNT_REQUIRED")
    void request_refundWithoutAccount_throws() {
        assertThatThrownBy(() -> payoutService.request(PayoutType.REFUND, user.getId(), 9_900,
                new RefundAccount("국민", " ", "김철수"), UUID.randomUUID(), null, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_ACCOUNT_REQUIRED);
        then(payoutRequestRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("송금 완료(환불): 잠근 뒤 최신 상태로 REQUESTED만 SENT, 처리자 기록, 리스너가 해당 USE를 REFUNDED로")
    void markSent_refund_marksRefunded() {
        UUID applicationId = UUID.randomUUID();
        PayoutRequest payout = locked(PayoutType.REFUND, applicationId, null);
        UUID adminId = UUID.randomUUID();

        payoutService.markSent(List.of(payout.getId()), adminId);

        then(payoutRequestRepository).should().refreshForUpdate(payout);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SENT);
        assertThat(payout.getSentBy()).isEqualTo(adminId);
        PayoutSentEvent event = capturedEvent();
        TicketService ticketService = mock(TicketService.class);
        DepositService depositService = mock(DepositService.class);
        new PayoutSentListener(ticketService, depositService).onSent(event);
        then(ticketService).should().completeRefund(applicationId);
        then(depositService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("송금 완료(보증금 반환): 리스너가 보증금을 RETURNED로")
    void markSent_depositReturn_marksReturned() {
        UUID depositId = UUID.randomUUID();
        PayoutRequest payout = locked(PayoutType.DEPOSIT_RETURN, UUID.randomUUID(), depositId);

        payoutService.markSent(List.of(payout.getId()), UUID.randomUUID());

        PayoutSentEvent event = capturedEvent();
        TicketService ticketService = mock(TicketService.class);
        DepositService depositService = mock(DepositService.class);
        new PayoutSentListener(ticketService, depositService).onSent(event);
        then(depositService).should().markReturned(depositId);
        then(ticketService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 SENT인 건을 다시 완료 처리해도 이벤트를 다시 내지 않는다(중복 클릭·동시 처리)")
    void markSent_alreadySent_noop() {
        PayoutRequest payout = locked(PayoutType.REFUND, UUID.randomUUID(), null);
        payout.markSent(UUID.randomUUID(), LocalDateTime.now());

        payoutService.markSent(List.of(payout.getId(), payout.getId()), UUID.randomUUID());

        then(payoutRequestRepository).should(times(1)).findByIdForUpdate(payout.getId());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("없는 송금 건이면 PAYOUT_NOT_FOUND")
    void markSent_unknown_throws() {
        UUID unknown = UUID.randomUUID();
        given(payoutRequestRepository.findByIdForUpdate(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> payoutService.markSent(List.of(unknown), UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYOUT_NOT_FOUND);
    }

    @Test
    @DisplayName("송금 후 30일 지난 건은 계좌번호를 끝 4자리만 남기고 가리며, 보증금 반환 건의 보증금 ID를 돌려준다")
    void maskAccountsSentBefore_keepsLast4() {
        UUID depositId = UUID.randomUUID();
        PayoutRequest payout = PayoutRequest.request(user, PayoutType.DEPOSIT_RETURN, 50_000, account, null, null, depositId);
        payout.markSent(null, LocalDateTime.now().minusDays(31));
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        given(payoutRequestRepository.findByStatusAndAccountMaskedFalseAndSentAtBefore(PayoutStatus.SENT, threshold))
                .willReturn(List.of(payout));

        List<UUID> depositIds = payoutService.maskAccountsSentBefore(threshold);

        assertThat(payout.getAccount().getAccountNumber()).isEqualTo("*******8901");
        assertThat(payout.getAccount().getBank()).isEqualTo("국민");
        assertThat(payout.isAccountMasked()).isTrue();
        assertThat(depositIds).containsExactly(depositId);
    }

    @Test
    @DisplayName("보증금 반환 철회: 아직 송금 전(REQUESTED)인 반환 요청만 지운다")
    void withdrawDepositReturn_onlyRequested() {
        UUID depositId = UUID.randomUUID();
        PayoutRequest payout = PayoutRequest.request(user, PayoutType.DEPOSIT_RETURN, 50_000, account, null, null, depositId);
        given(payoutRequestRepository.findByDepositId(depositId)).willReturn(Optional.of(payout));

        payoutService.withdrawDepositReturn(depositId);
        then(payoutRequestRepository).should().delete(payout);

        payout.markSent(null, LocalDateTime.now());
        payoutService.withdrawDepositReturn(depositId);
        then(payoutRequestRepository).should(times(1)).delete(any());
    }

    private PayoutSentEvent capturedEvent() {
        ArgumentCaptor<PayoutSentEvent> captor = ArgumentCaptor.forClass(PayoutSentEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        return captor.getValue();
    }
}
