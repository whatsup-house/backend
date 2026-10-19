package com.whatsuphouse.backend.domain.ticket.enums;

public enum TicketTransactionType {
    ISSUE, USE, REFUND, ADMIN_ADD, ADMIN_DEDUCT, EXPIRE,
    EXTEND  // 멤버십 매칭 실패 기간 연장(수량 0). (KAN-401)
}
