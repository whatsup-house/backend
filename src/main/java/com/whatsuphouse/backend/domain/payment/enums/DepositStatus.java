package com.whatsuphouse.backend.domain.payment.enums;

/** 노쇼 보증금 상태. (설계 4.3, KAN-402) */
public enum DepositStatus {
    PENDING,        // 입금 대기
    PAID,           // 입금 확인
    RETURN_PENDING, // 반환 대기(체크인·사전 취소). 운영자 이체 후 RETURNED
    RETURNED,
    FORFEITED       // 몰수(노쇼 확정·당일 취소)
}
