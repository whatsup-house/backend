package com.whatsuphouse.backend.domain.payment.dto.request;

import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 예외 건 입금 알림을 결제 대기 건에 수동 연결. 대상은 GET /api/admin/payments/pending-targets 목록에서 고른다. */
@Getter
@NoArgsConstructor
public class DepositNotificationLinkRequest {

    @NotNull
    @Schema(description = "결제 대상 종류", example = "TICKET_PASS")
    private PaymentTargetType targetType;

    @NotNull
    @Schema(description = "결제 대상 ID(pending-targets의 targetId)", example = "b3f1c2d4-...")
    private UUID targetId;
}
