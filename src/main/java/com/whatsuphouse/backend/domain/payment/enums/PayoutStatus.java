package com.whatsuphouse.backend.domain.payment.enums;

/** 송금 요청 상태. 운영자가 실제 이체 후 SENT로 바꾼다. 데모 회원 건은 요청 즉시 SENT. (KAN-404) */
public enum PayoutStatus {
    REQUESTED, SENT
}
