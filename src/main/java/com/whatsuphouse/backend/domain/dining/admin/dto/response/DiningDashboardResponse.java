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

        public static SessionCard of(GatheringSession session, long paidCount, long tableCount,
                                     boolean isMatchRunDone, long openExceptionCount) {
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
                    .build();
        }
    }
}
