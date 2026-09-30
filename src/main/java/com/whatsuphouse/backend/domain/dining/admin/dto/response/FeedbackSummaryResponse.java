package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** 회차 피드백 요약. 확정·종료(CONFIRMED|DONE) 테이블 기준. 평균은 응답이 없으면 null. (KAN-350) */
@Getter
@Builder
public class FeedbackSummaryResponse {

    @Schema(description = "피드백 응답 수", example = "9")
    private int responseCount;

    @Schema(description = "대상 멤버 수(확정·종료 테이블, 취소한 신청 제외)", example = "12")
    private int memberCount;

    @Schema(description = "응답률 0~1", example = "0.75")
    private double responseRate;

    @Schema(description = "테이블 만족도 평균", example = "4.33", nullable = true)
    private Double avgTable;

    @Schema(description = "대화 만족도 평균", example = "4.11", nullable = true)
    private Double avgTalk;

    @Schema(description = "식당 만족도 평균", example = "3.89", nullable = true)
    private Double avgVenue;

    @Schema(description = "재참여 의향 YES 수", example = "6")
    private long rejoinYes;

    @Schema(description = "재참여 의향 MAYBE 수", example = "2")
    private long rejoinMaybe;

    @Schema(description = "재참여 의향 NO 수", example = "1")
    private long rejoinNo;

    @Schema(description = "신고 수", example = "1")
    private long reportCount;

    @Schema(description = "테이블별 요약(생성 순)")
    private List<TableSummary> byTable;

    @Getter
    @Builder
    public static class TableSummary {

        @Schema(description = "테이블 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        private UUID tableId;

        @Schema(description = "피드백 응답 수", example = "3")
        private int responseCount;

        @Schema(description = "테이블 만족도 평균", example = "4.67", nullable = true)
        private Double avgTable;

        @Schema(description = "대화 만족도 평균", example = "4.33", nullable = true)
        private Double avgTalk;

        @Schema(description = "식당 만족도 평균", example = "4.0", nullable = true)
        private Double avgVenue;
    }
}
