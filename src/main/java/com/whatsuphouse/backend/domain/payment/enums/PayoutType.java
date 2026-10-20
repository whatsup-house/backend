package com.whatsuphouse.backend.domain.payment.enums;

/** 운영자 송금 종류. REFUND = 이용권 환불(매칭 실패 환불 선택·멤버십 환불), DEPOSIT_RETURN = 보증금 반환. (KAN-404) */
public enum PayoutType {
    REFUND, DEPOSIT_RETURN
}
