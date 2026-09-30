package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.enums.SafetyAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ExceptionCaseResponse {

    @Schema(description = "예외 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "유형", example = "VENUE")
    private ExceptionCaseType type;

    @Schema(description = "상태", example = "OPEN")
    private ExceptionCaseStatus status;

    @Schema(description = "관련 회차 ID", nullable = true)
    private UUID sessionId;

    @Schema(description = "관련 테이블 ID(현재는 매칭 그룹 ID)", nullable = true)
    private UUID tableId;

    @Schema(description = "관련 신청 ID", nullable = true)
    private UUID applicationId;

    @Schema(description = "발생 사유", example = "회차 식당 수용 테이블이 부족합니다.")
    private String reason;

    @Schema(description = "SAFETY 처리 조치", example = "WARN", nullable = true)
    private SafetyAction action;

    @Schema(description = "처리한 관리자 ID", nullable = true)
    private UUID resolvedBy;

    @Schema(description = "처리 메모", nullable = true)
    private String resolutionNote;

    @Schema(description = "발생 시각", example = "2026-10-01T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "처리 시각", nullable = true)
    private LocalDateTime resolvedAt;

    public static ExceptionCaseResponse from(ExceptionCase exceptionCase) {
        return ExceptionCaseResponse.builder()
                .id(exceptionCase.getId())
                .type(exceptionCase.getType())
                .status(exceptionCase.getStatus())
                .sessionId(exceptionCase.getSessionId())
                .tableId(exceptionCase.getTableId())
                .applicationId(exceptionCase.getApplicationId())
                .reason(exceptionCase.getReason())
                .action(exceptionCase.getAction())
                .resolvedBy(exceptionCase.getResolvedBy())
                .resolutionNote(exceptionCase.getResolutionNote())
                .createdAt(exceptionCase.getCreatedAt())
                .resolvedAt(exceptionCase.getResolvedAt())
                .build();
    }
}
