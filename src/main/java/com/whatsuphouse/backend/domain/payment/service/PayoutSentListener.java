package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.payment.event.PayoutSentEvent;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 송금 완료 후속 처리. 송금 완료와 같은 트랜잭션에서 돈 상태를 맞춘다(동기 리스너).
 * PayoutService가 TicketService·DepositService를 직접 부르면 빈 순환이 생겨 이벤트로 분리했다. (KAN-404)
 */
@Component
@RequiredArgsConstructor
public class PayoutSentListener {

    private final TicketService ticketService;
    private final DepositService depositService;

    @EventListener
    public void onSent(PayoutSentEvent event) {
        switch (event.type()) {
            // 멤버십 환불(신청 없음)은 이용권이 이미 CANCELLED라 더 바꿀 것이 없다.
            case REFUND -> {
                if (event.applicationId() != null) {
                    ticketService.completeRefund(event.applicationId());
                }
            }
            case DEPOSIT_RETURN -> depositService.markReturned(event.depositId());
        }
    }
}
