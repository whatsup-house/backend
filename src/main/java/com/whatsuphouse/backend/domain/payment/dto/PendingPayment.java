package com.whatsuphouse.backend.domain.payment.dto;

import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;

import java.util.UUID;

/** 입금 대기 중인 건 하나. 입금 대사 후보로 쓰인다. */
public record PendingPayment(PaymentTargetType type, UUID targetId, UUID applicationId, String payerName, int amount) {
}
