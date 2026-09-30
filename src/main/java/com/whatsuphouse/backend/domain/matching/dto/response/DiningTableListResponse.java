package com.whatsuphouse.backend.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsuphouse.backend.domain.matching.entity.ScoreDetail;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 회차 콘솔 테이블 탭: 해체되지 않은 테이블 + 최신 실행의 미배정 + 최신 실행 집계. */
@Getter
@Builder
public class DiningTableListResponse {

    @Schema(description = "테이블 목록(해체된 테이블 제외), 생성 순")
    private List<TableView> tables;

    @Schema(description = "최신 실행에서 앉지 못한 신청(이후 활성 테이블에 들어간 신청 제외)")
    private List<UnassignedView> unassigned;

    @Schema(description = "최신 실행 집계. 실행 이력이 없으면 null")
    private MatchRunResponse lastRun;

    @Getter
    @Builder
    public static class TableView {
        @Schema(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;
        @Schema(description = "상태", example = "PROPOSED")
        private DiningTableStatus status;
        @Schema(description = "그룹 점수", example = "0.6123")
        private BigDecimal groupScore;
        @Schema(description = "점수 내역 {pairAvg, pairMin, penalties}")
        private ScoreDetail scoreDetail;
        @Schema(description = "자동 확정 예정 시각", example = "2026-10-08T23:00:00")
        private LocalDateTime confirmAt;
        @Schema(description = "잠금 여부(재실행 시 유지)", example = "false")
        private boolean locked;
        @Schema(description = "배정 식당 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID venueId;
        @Schema(description = "멤버, 좌석 순")
        private List<MemberView> members;
    }

    @Getter
    @Builder
    public static class MemberView {
        @Schema(description = "테이블 멤버 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID memberId;
        @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID applicationId;
        @Schema(description = "회원 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID userId;
        @Schema(description = "닉네임", example = "우연한고양이")
        private String nickname;
        @Schema(description = "출생연도(표준 질문 답)", example = "1995")
        private Integer birthYear;
        @Schema(description = "성별(표준 질문 답)", example = "FEMALE")
        private String gender;
        @Schema(description = "MBTI(표준 질문 답)", example = "ENFP")
        private String mbti;
        @Schema(description = "배정 경위", example = "INITIAL")
        private AssignReason assignReason;
        @Schema(description = "수동 배정 여부", example = "false")
        @JsonProperty("isManual")
        private boolean isManual;
    }

    @Getter
    @Builder
    public static class UnassignedView {
        @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID applicationId;
        @Schema(description = "닉네임", example = "우연한고양이")
        private String nickname;
        @Schema(description = "미배정 사유", example = "NOT_ENOUGH_PEOPLE")
        private UnassignedReason reason;
    }
}
