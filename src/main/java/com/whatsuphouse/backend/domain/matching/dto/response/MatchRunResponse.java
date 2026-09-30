package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** 매칭 실행 1회 집계. */
@Getter
@Builder
public class MatchRunResponse {

    @Schema(description = "실행 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "시작 시각", example = "2026-10-08T21:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "종료 시각", example = "2026-10-08T21:00:01")
    private LocalDateTime finishedAt;

    @Schema(description = "실행 주체", example = "MANUAL")
    private MatchRunTrigger triggeredBy;

    @Schema(description = "후보 인원", example = "13")
    private int candidateCount;

    @Schema(description = "만든 테이블 수", example = "3")
    private int tableCount;

    @Schema(description = "tableSizeMax를 넘어 나눈 덩어리 수", example = "1")
    private int splitCount;

    @Schema(description = "미완성 조각을 합쳐 만든 테이블 수", example = "0")
    private int mergeCount;

    @Schema(description = "기존 테이블에 재배치한 인원", example = "1")
    private int reallocatedCount;

    @Schema(description = "미배정 인원", example = "0")
    private int unassignedCount;

    @Schema(description = "알고리즘 버전", example = "rule-v2")
    private String algorithmVersion;

    public static MatchRunResponse from(MatchRun run) {
        return MatchRunResponse.builder()
                .id(run.getId())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .triggeredBy(run.getTriggeredBy())
                .candidateCount(run.getCandidateCount())
                .tableCount(run.getTableCount())
                .splitCount(run.getSplitCount())
                .mergeCount(run.getMergeCount())
                .reallocatedCount(run.getReallocatedCount())
                .unassignedCount(run.getUnassignedCount())
                .algorithmVersion(run.getAlgorithmVersion())
                .build();
    }
}
