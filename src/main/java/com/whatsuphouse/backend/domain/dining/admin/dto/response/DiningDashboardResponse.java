package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 우연한 식탁 운영 대시보드. 다가오는 우연한 식탁 회차 기준. */
@Getter
@Builder
public class DiningDashboardResponse {

    @Schema(description = "다가오는 회차 수(오늘 포함, 취소 제외)", example = "3")
    private long upcomingSessionCount;

    @Schema(description = "매칭 대기 신청자 수(WAITING·REALLOCATING, 반려·취소 제외). 여러 회차를 고른 신청은 1명", example = "18")
    private long waitingApplicantCount;

    @Schema(description = "결제 완료 신청자 수(이용권 차감 완료 = 승인·출석). 여러 회차를 고른 신청은 1명", example = "15")
    private long paidApplicantCount;

    @Schema(description = "제안 중 테이블 수", example = "2")
    private long proposedTableCount;

    @Schema(description = "확정 테이블 수", example = "1")
    private long confirmedTableCount;

    @Schema(description = "열린 예외 수(회차 무관 전체)", example = "4")
    private long openExceptionCount;

    // ── 운영 지표(KAN-437). 신청·테이블 지표는 위 타일과 같이 다가오는 회차(숨김·체험 회원 제외), 예외 지표는 열린 예외 수와 같은 기준 ──

    @Schema(description = "신청자 수(반려·취소 제외). 여러 회차를 고른 신청은 1명", example = "24")
    private long totalApplicantCount;

    @Schema(description = "매칭 실행 횟수(재실행 포함)", example = "3")
    private long matchRunCount;

    @Schema(description = "테이블에 배정된 신청자 수(매칭 상태 CONFIRM_PENDING·CONFIRMED)", example = "16")
    private long matchedApplicantCount;

    @Schema(description = "재배치 대기 신청자 수(매칭 상태 REALLOCATING)", example = "2")
    private long reallocatedApplicantCount;

    @Schema(description = "테이블 확정 신청자 수(매칭 상태 CONFIRMED)", example = "12")
    private long confirmedApplicantCount;

    @Schema(description = "매칭 성공률(0~1): 결제 완료 신청자 중 테이블 확정 비율. 결제 완료가 없으면 0", example = "0.8")
    private double matchSuccessRate;

    @Schema(description = "매칭 실패 해결 선택 응답 대기 수(ALTERNATIVE_OFFERED·NO_MATCH)", example = "1")
    private long resolutionPendingCount;

    @Schema(description = "열린 CONFLICT 예외 수", example = "0")
    private long conflictCount;

    @Schema(description = "확정됐는데 식당이 없는 테이블 수", example = "0")
    private long noVenueTableCount;

    @Schema(description = "열린 NOTIFICATION(알림·채팅방 실패) 예외 수", example = "0")
    private long notificationFailureCount;

    @Schema(description = "환불 실패 수: 열린 REFUND 예외 + 환불 실패(REFUND_FAILED) 이용권 차감", example = "0")
    private long refundFailureCount;

    @Schema(description = "노쇼 후보 수(종료 후 체크인 없음, 운영자 확정 전)", example = "0")
    private long noShowCandidateCount;

    @Schema(description = "열린 SAFETY(신고) 예외 수", example = "0")
    private long openReportCount;

    @Schema(description = "다가오는 회차 카드, 날짜·시작 시간 순")
    private List<SessionCard> sessions;

    @Getter
    @Builder
    public static class SessionCard {

        @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID sessionId;

        @Schema(description = "모임 종류 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID gatheringId;

        @Schema(description = "모임 이름", example = "우연한 식탁")
        private String title;

        @Schema(description = "행사 날짜", example = "2026-10-10")
        private LocalDate eventDate;

        @Schema(description = "시작 시간", example = "19:00:00", nullable = true)
        private LocalTime startTime;

        @Schema(description = "장소(지역) 이름", example = "을지로", nullable = true)
        private String locationName;

        @Schema(description = "회차 상태. 날짜가 지난 모집중 회차는 DONE", example = "OPEN")
        private GatheringSessionStatus status;

        @Schema(description = "결제 완료 신청자 수", example = "8")
        private long paidCount;

        @Schema(description = "정원", example = "12")
        private int maxAttendees;

        @Schema(description = "매칭 실행 예정 시각", example = "2026-10-08T12:00:00", nullable = true)
        private LocalDateTime matchRunAt;

        @Schema(description = "매칭 실행 완료 여부(테이블이 하나라도 만들어졌으면 true)", example = "false")
        private Boolean isMatchRunDone;

        @Schema(description = "테이블 수(제안 중 + 확정)", example = "2")
        private long tableCount;

        @Schema(description = "이 회차의 열린 예외 수", example = "1")
        private long openExceptionCount;

        // 심사용 숨김 종류 회차. 카드에는 나오지만 타일 집계에서는 빠진다. 화면이 "심사용"으로 표시한다. (KAN-416)
        @Schema(description = "심사용 숨김 종류 회차 여부", example = "false")
        private boolean hidden;

        public static SessionCard of(GatheringSession session, long paidCount, long tableCount,
                                     boolean isMatchRunDone, long openExceptionCount, boolean hidden) {
            return SessionCard.builder()
                    .sessionId(session.getId())
                    .gatheringId(session.getGathering().getId())
                    .title(session.getGathering().getTitle())
                    .eventDate(session.getEventDate())
                    .startTime(session.getStartTime())
                    .locationName(session.getLocation() != null ? session.getLocation().getName() : null)
                    .status(session.getEffectiveSessionStatus())
                    .paidCount(paidCount)
                    .maxAttendees(session.getMaxAttendees())
                    .matchRunAt(session.getMatchRunAt())
                    .isMatchRunDone(isMatchRunDone)
                    .tableCount(tableCount)
                    .openExceptionCount(openExceptionCount)
                    .hidden(hidden)
                    .build();
        }
    }
}
