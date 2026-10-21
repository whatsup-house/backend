package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.ApplicationStatusLog;
import com.whatsuphouse.backend.domain.application.enums.StatusLogField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 신청 상태 변경 이력 한 줄. GET /api/admin/dining/applications/{id}/history (KAN-437) */
@Getter
@Builder
public class ApplicationStatusHistoryResponse {

    @Schema(description = "항목(STATUS: 신청 상태, MATCH_STATUS: 매칭 상태, PAYMENT: 통합 결제 상태)", example = "MATCH_STATUS")
    private StatusLogField field;

    @Schema(description = "바뀌기 전 값. 그 항목의 첫 기록이면 null", example = "WAITING", nullable = true)
    private String from;

    @Schema(description = "바뀐 값", example = "CONFIRM_PENDING", nullable = true)
    private String to;

    @Schema(description = "바꾼 회원 이름(관리자·참가자). 시스템 처리면 null", example = "김운영", nullable = true)
    private String actorName;

    @Schema(description = "사유", example = "매칭 배정", nullable = true)
    private String reason;

    @Schema(description = "기록 시각", example = "2026-10-21T12:00:00")
    private LocalDateTime at;

    public static ApplicationStatusHistoryResponse of(ApplicationStatusLog log, String actorName) {
        return ApplicationStatusHistoryResponse.builder()
                .field(log.getField())
                .from(log.getFromValue())
                .to(log.getToValue())
                .actorName(actorName)
                .reason(log.getReason())
                .at(log.getCreatedAt())
                .build();
    }
}
