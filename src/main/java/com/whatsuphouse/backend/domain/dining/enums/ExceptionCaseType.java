package com.whatsuphouse.backend.domain.dining.enums;

/** 예외함 유형. 자동 처리할 수 없어 운영자가 봐야 하는 건만 쌓인다. (우연한 식탁 v2 설계 4.10) */
public enum ExceptionCaseType {
    PAYMENT,        // 결제 데이터 불일치(이용권 차감 실패 등)
    DATA,           // 필수 답변 누락으로 하드 조건 평가 불가
    VENUE,          // 식당 수용 부족
    NOTIFICATION,   // 알림·채팅방 생성 3회 실패
    SAFETY,         // 신고 접수
    CONFLICT,       // 확정 후 취소 미해결, 수동 조정 후 하드 조건 위반
    REFUND          // 자동 환불 실패
}
