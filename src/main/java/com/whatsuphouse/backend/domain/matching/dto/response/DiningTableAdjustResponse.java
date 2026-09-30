package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.matching.service.MatchingEngine;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * 테이블 수동 조정 결과. 위반이 있으면 조정은 롤백되고 400 TABLE_RULE_VIOLATION의 params.violations로
 * 같은 Violation 목록이 내려간다. (KAN-347)
 */
@Getter
@Builder
public class DiningTableAdjustResponse {

    @Schema(description = "조정 후 갱신된 활성 테이블(해체된 테이블 제외)")
    private List<DiningTableListResponse.TableView> tables;

    @Schema(description = "하드 조건 재검증 결과")
    private Validation validation;

    @Getter
    @AllArgsConstructor
    public static class Validation {
        @Schema(description = "통과 여부", example = "true")
        private boolean valid;

        @Schema(description = "위반 목록. 통과면 빈 배열")
        private List<Violation> violations;
    }

    @Getter
    @AllArgsConstructor
    public static class Violation {
        @Schema(description = "위반한 테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID tableId;

        @Schema(description = "위반한 하드 조건", example = "TABLE_SIZE")
        private MatchingEngine.HardRule rule;

        @Schema(description = "설명", example = "인원 7명이 4~6명 범위를 벗어납니다.")
        private String message;
    }
}
