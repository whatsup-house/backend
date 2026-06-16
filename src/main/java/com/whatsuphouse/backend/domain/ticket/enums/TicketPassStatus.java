package com.whatsuphouse.backend.domain.ticket.enums;

public enum TicketPassStatus {
    PENDING,   // 구매 요청 — 입금 확인 대기 (사용 불가)
    ACTIVE,    // 입금 확인 완료 — 사용 가능
    USED_UP,   // 잔여 0회
    CANCELLED  // 취소됨
}
