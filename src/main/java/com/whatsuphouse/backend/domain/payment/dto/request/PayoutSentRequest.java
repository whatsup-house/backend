package com.whatsuphouse.backend.domain.payment.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** 운영자 송금 완료 처리. 이체를 끝낸 송금 요청 ID 목록. */
public record PayoutSentRequest(@NotEmpty @Size(max = 200) List<UUID> ids) {
}
