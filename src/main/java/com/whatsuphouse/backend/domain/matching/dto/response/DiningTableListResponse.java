package com.whatsuphouse.backend.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatsuphouse.backend.domain.matching.entity.ScoreDetail;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.TableNoticeStatus;
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

    @Schema(description = "최신 실행의 지역별 결과, 처리 순. 실행 이력이 없거나 지역 매칭 전 실행이면 빈 목록")
    private List<RegionResultView> regionResults;

    @Getter
    @Builder
    public static class TableView {
        @Schema(description = "테이블 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;
        @Schema(description = "상태", example = "PROPOSED")
        private DiningTableStatus status;
        @Schema(description = "테이블 지역 라벨. 지역 매칭 전 테이블은 null", example = "강남/신논현", nullable = true)
        private String region;
        @Schema(description = "그룹 점수", example = "0.6123")
        private BigDecimal groupScore;
        @Schema(description = "점수 내역 {pairAvg, pairMin, penalties}")
        private ScoreDetail scoreDetail;
        @Schema(description = "자동 확정 예정 시각", example = "2026-10-08T23:00:00")
        private LocalDateTime confirmAt;
        @Schema(description = "매칭(테이블 생성) 시각", example = "2026-10-08T22:30:00", nullable = true)
        private LocalDateTime matchedAt;
        @Schema(description = "확정 시각. 확정 전이면 null", example = "2026-10-08T23:00:00", nullable = true)
        private LocalDateTime confirmedAt;
        @Schema(description = "잠금 여부(재실행 시 유지)", example = "false")
        private boolean locked;
        @Schema(description = "배정 식당 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID venueId;
        @Schema(description = "테이블 채팅방 ID. 확정 전이거나 생성 실패면 null", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
        private UUID chatRoomId;
        @Schema(description = "멤버 최소 나이(올해 - 출생연도, 만 나이 아님). 출생연도를 아는 멤버가 없으면 null", example = "27", nullable = true)
        private Integer ageMin;
        @Schema(description = "멤버 최대 나이(올해 - 출생연도, 만 나이 아님). 출생연도를 아는 멤버가 없으면 null", example = "33", nullable = true)
        private Integer ageMax;
        @Schema(description = "남성 멤버 수(성별 답 MALE)", example = "2")
        private int maleCount;
        @Schema(description = "여성 멤버 수(성별 답 FEMALE)", example = "2")
        private int femaleCount;
        @Schema(description = "멤버 MBTI, 좌석 순(모르는 멤버는 빠진다, 중복 그대로)", example = "[\"ENFP\", \"ISTJ\"]")
        private List<String> mbtiList;
        @Schema(description = "멤버 전원이 고른 관심사", example = "[\"여행\"]")
        private List<String> commonInterests;
        @Schema(description = "최신 확정 안내(CONFIRM_MESSAGE) 결과. NONE: 시도 없음, SENT: 발송됨, FAILED: 실패", example = "SENT")
        private TableNoticeStatus noticeStatus;
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
        @Schema(description = "재배치 대기(REALLOCATING)로 바뀐 횟수", example = "1")
        private int reallocationCount;
        @Schema(description = "테이블 회차를 희망 회차로 골랐는지", example = "true")
        private boolean wantsSession;
        @Schema(description = "테이블 지역을 희망 지역으로 골랐는지. 지역 없는 테이블은 true", example = "true")
        private boolean wantsRegion;
        @Schema(description = "같은 테이블 누군가와 이전에(확정·완료 테이블에서) 함께 앉은 적이 있는지", example = "false")
        private boolean metBefore;
        @Schema(description = "같은 테이블 누군가와 제외 관계(피하고 싶음·신고)가 있는지", example = "false")
        private boolean conflict;
    }

    @Getter
    @Builder
    public static class UnassignedView {
        @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID applicationId;
        @Schema(description = "닉네임", example = "우연한고양이")
        private String nickname;
        @Schema(description = "미배정 사유", example = "REGION_TOO_SMALL")
        private UnassignedReason reason;
        @Schema(description = "재배치 대기(REALLOCATING)로 바뀐 횟수. 신청을 찾지 못하면 0", example = "1")
        private int reallocationCount;
        @Schema(description = "미배정된 매칭 실행 시각", example = "2026-10-08T22:30:00")
        private LocalDateTime failedAt;
    }

    @Getter
    @Builder
    public static class RegionResultView {
        @Schema(description = "지역 라벨", example = "강남/신논현")
        private String region;
        @Schema(description = "이 지역 매칭에 넣은 인원(앞 지역 테이블에 앉은 사람 제외)", example = "9")
        private int candidateCount;
        @Schema(description = "매칭 결과 테이블 수(채택 여부와 무관)", example = "2")
        private int tableCount;
        @Schema(description = "채택 여부. 테이블 수가 지역 최소 테이블 수 이상일 때만 테이블을 만든다", example = "true")
        private boolean adopted;
    }
}
