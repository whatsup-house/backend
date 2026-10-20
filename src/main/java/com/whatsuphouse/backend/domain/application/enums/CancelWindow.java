package com.whatsuphouse.backend.domain.application.enums;

/** 우연한 식탁 취소 시점. 마감 = 회차 날짜 D-1 00:00, 당일 = 회차 날짜 00:00 이후. (KAN-402) */
public enum CancelWindow {
    BEFORE_DEADLINE,    // 1회권 100% 환불, 보증금 100% 반환
    AFTER_DEADLINE,     // 1회권 50% 환불, 보증금 50% 반환
    SAME_DAY            // 테이블에 앉은 신청: 환불 없음, 보증금 몰수 + 보증금 대상 지정(노쇼와 동일). 앉지 않았으면 마감 후와 같다
}
