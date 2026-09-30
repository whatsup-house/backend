package com.whatsuphouse.backend.domain.ticket.enums;

/**
 * 이용권 차감(USE 거래) 레코드의 상태. 다른 거래 유형은 NULL. (우연한 식탁 v2 설계 3장, KAN-342)
 * DEDUCTED → RESTORED(취소 복원) | REFUND_REQUESTED → REFUND_PROCESSING → REFUNDED | REFUND_FAILED.
 */
public enum TicketDeductionStatus {
    DEDUCTED, RESTORED, REFUND_REQUESTED, REFUND_PROCESSING, REFUNDED, REFUND_FAILED
}
