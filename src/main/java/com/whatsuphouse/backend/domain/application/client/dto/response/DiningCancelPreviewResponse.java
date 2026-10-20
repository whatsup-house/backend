package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import io.swagger.v3.oas.annotations.media.Schema;

/** 지금 취소하면 적용되는 결과. GET /api/dining/applications/{id}/cancel-preview (설계 4.1 PAY-02, KAN-402) */
public record DiningCancelPreviewResponse(
        @Schema(description = "취소 시점", example = "AFTER_DEADLINE")
        CancelWindow window,
        @Schema(description = "1회권이 복원되는지(마감 전 + 1회권 사용 신청)", example = "false")
        boolean ticketRestored,
        @Schema(description = "돈으로 환불되는지. 취소는 환불하지 않는다", example = "false")
        boolean refundable,
        @Schema(description = "보증금 처리: RETURN 반환, FORFEIT 몰수, NONE 입금된 보증금 없음", example = "RETURN")
        DepositOutcome depositOutcome,
        @Schema(description = "제재(보증금 대상 지정) 여부. 당일 취소면 true", example = "false")
        boolean sanction
) {
    public enum DepositOutcome { RETURN, FORFEIT, NONE }
}
