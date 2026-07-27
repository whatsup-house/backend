package com.whatsuphouse.backend.domain.mileage.dto.response;

import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MileageHistoryResponse {

    @Schema(description = "마일리지 이력 ID")
    private UUID id;

    @Schema(description = "마일리지 타입")
    private MileageType type;

    @Schema(description = "적립/차감 금액", example = "1000")
    private Integer amount;

    @Schema(description = "처리 후 잔액", example = "2500")
    private Integer balanceAfter;

    @Schema(description = "관련 대상 ID")
    private UUID relatedId;

    @Schema(description = "관리자 조정 사유 (ADMIN_ADJUST 타입일 때만 값 존재)")
    private String adjustReason;

    @Schema(description = "적립/차감 일시")
    private LocalDateTime createdAt;

    public static MileageHistoryResponse from(MileageHistory history) {
        return MileageHistoryResponse.builder()
                .id(history.getId())
                .type(history.getType())
                .amount(history.getAmount())
                .balanceAfter(history.getBalanceAfter())
                .relatedId(history.getRelatedId())
                .adjustReason(history.getAdjustReason())
                .createdAt(history.getEarnedDate())
                .build();
    }
}
