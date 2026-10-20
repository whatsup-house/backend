package com.whatsuphouse.backend.domain.payment.dto;

import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;

import java.util.UUID;

/**
 * 입금 대기 중인 건 하나. 입금 대사 후보로 쓰인다.
 * targetName = 이용권 상품명 또는 모임 제목. 운영자 후보 목록에서만 채운다(대사 경로에서는 null). (KAN-426)
 */
public record PendingPayment(PaymentTargetType type, UUID targetId, UUID applicationId, String payerName, int amount,
                             String targetName) {

    public PendingPayment(PaymentTargetType type, UUID targetId, UUID applicationId, String payerName, int amount) {
        this(type, targetId, applicationId, payerName, amount, null);
    }

    public PendingPayment withTargetName(String name) {
        return new PendingPayment(type, targetId, applicationId, payerName, amount, name);
    }
}
