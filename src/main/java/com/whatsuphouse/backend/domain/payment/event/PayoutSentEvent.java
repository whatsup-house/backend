package com.whatsuphouse.backend.domain.payment.event;

import com.whatsuphouse.backend.domain.payment.enums.PayoutType;

import java.util.UUID;

/** 송금 완료. 같은 트랜잭션의 리스너가 후속 상태를 맞춘다(REFUND → 차감 기록 REFUNDED, DEPOSIT_RETURN → 보증금 RETURNED). (KAN-404) */
public record PayoutSentEvent(UUID payoutId, PayoutType type, UUID applicationId, UUID depositId) {
}
