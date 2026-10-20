package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import io.swagger.v3.oas.annotations.media.Schema;

/** 지금 취소하면 적용되는 결과. GET /api/dining/applications/{id}/cancel-preview (설계 4.1 PAY-02, KAN-402, KAN-418) */
public record DiningCancelPreviewResponse(
        @Schema(description = "취소 시점", example = "AFTER_DEADLINE")
        CancelWindow window,
        @Schema(description = "1회권이 복원되는지. 취소는 이용권을 복원하지 않고 돈으로 환불하므로 항상 false", example = "false")
        boolean ticketRestored,
        @Schema(description = "이용권 금액을 돈으로 환불하는지(ticketRefundAmount > 0)", example = "true")
        boolean refundable,
        @Schema(description = "보증금 처리: RETURN 전액 반환, PARTIAL 50% 반환(나머지 몰수), FORFEIT 몰수, NONE 입금된 보증금 없음",
                example = "PARTIAL")
        DepositOutcome depositOutcome,
        @Schema(description = "제재(보증금 대상 지정) 여부. 테이블에 앉은 신청의 당일 취소면 true", example = "false")
        boolean sanction,
        @Schema(description = "환불될 1회권 금액(원). 마감 전 1회분 전액, 마감 후 50%(원 단위 버림), 당일·멤버십·무료는 0", example = "7500")
        int ticketRefundAmount,
        @Schema(description = "반환될 보증금(원). 마감 전 전액, 마감 후 50%, 당일(테이블에 앉음)·보증금 없음은 0", example = "25000")
        int depositReturnAmount,
        @Schema(description = "취소 요청에 환불 계좌(refundAccount)가 필요한지. 이용권 환불이 있을 때만 true(보증금은 신청 때 받은 계좌로 반환)",
                example = "true")
        boolean requiresRefundAccount
) {
    public enum DepositOutcome { RETURN, PARTIAL, FORFEIT, NONE }
}
