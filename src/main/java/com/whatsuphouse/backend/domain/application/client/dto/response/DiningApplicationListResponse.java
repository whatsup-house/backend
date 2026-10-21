package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 내 우연한 식탁 신청 목록. GET /api/dining/me/applications (KAN-342) */
@Getter
@Builder
public class DiningApplicationListResponse {

    @Schema(description = "내 우연한 식탁 신청 목록 (최신 신청 순). 최근 30일 안에 취소한 신청도 status=CANCELLED로 포함")
    private List<Item> applications;

    @Getter
    @Builder
    public static class Item {
        @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;

        @Schema(description = "모임 종류")
        private GatheringInfo gathering;

        @Schema(description = "희망 회차 (우선순위 순)")
        private List<SessionInfo> candidateSessions;

        @Schema(description = "배정 회차. 매칭 전이면 null")
        private SessionInfo assignedSession;

        @Schema(description = "희망 지역 라벨, 지역 선언 순서(GET /api/dining/regions 순서)", example = "[\"홍대/합정\", \"강남/신논현\"]")
        private List<String> candidateRegions;

        @Schema(description = "신청 상태", example = "CONFIRMED")
        private ApplicationStatus status;

        @Schema(description = "매칭 상태. 접수 시점부터 WAITING이며 결제 상태와는 별개", example = "WAITING")
        private MatchStatus matchStatus;

        @Schema(description = "이용권 차감 상태. 차감 기록이 없으면(결제 대기 등) null", example = "DEDUCTED")
        private TicketDeductionStatus ticketStatus;

        @Schema(description = "배정 테이블 요약(활성 PROPOSED|CONFIRMED 테이블). 배정 전이면 null")
        private TableInfo table;

        @Schema(description = "응답을 기다리는 매칭 실패 해결 선택 ID(GET /api/dining/resolutions/{id}). 없으면 null",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
        private UUID resolutionId;

        @Schema(description = "노쇼 보증금. 보증금 대상 신청이 아니면 null", nullable = true)
        private DepositInfo deposit;

        public static Item of(Application application, List<GatheringSession> candidateSessions,
                              TicketDeductionStatus ticketStatus, DiningTable table, UUID resolutionId, Deposit deposit) {
            GatheringSession assigned = application.getSession();
            return Item.builder()
                    .id(application.getId())
                    .gathering(GatheringInfo.builder()
                            .id(application.getGathering().getId())
                            .title(application.getGathering().getTitle())
                            .build())
                    .candidateSessions(candidateSessions.stream().map(session -> SessionInfo.from(session, table)).toList())
                    .assignedSession(assigned != null ? SessionInfo.from(assigned, table) : null)
                    .candidateRegions(DiningRegion.labelsOf(application.getCandidateRegions()))
                    .status(application.getStatus())
                    .matchStatus(application.getMatchStatus())
                    .ticketStatus(ticketStatus)
                    .table(table != null ? TableInfo.from(table) : null)
                    .resolutionId(resolutionId)
                    .deposit(deposit != null ? new DepositInfo(deposit.getStatus(), deposit.getAmount(), deposit.getReturnAmount()) : null)
                    .build();
        }
    }

    /** 신청의 노쇼 보증금 요약. (KAN-402) */
    public record DepositInfo(
            @Schema(description = "보증금 상태", example = "PAID") DepositStatus status,
            @Schema(description = "보증금 금액(원)", example = "50000") int amount,
            @Schema(description = "반환(예정) 금액(원). 마감 후 취소면 50%. 반환 전·몰수면 null", example = "25000", nullable = true)
            Integer returnAmount) {
    }

    @Getter
    @Builder
    public static class GatheringInfo {
        @Schema(description = "모임 종류 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        private UUID id;

        @Schema(description = "모임 제목", example = "우연한 식탁")
        private String title;
    }

    @Getter
    @Builder
    public static class SessionInfo {
        @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;

        @Schema(description = "행사 날짜", example = "2026-10-10")
        private LocalDate eventDate;

        @Schema(description = "시작 시간", example = "19:00:00")
        private LocalTime startTime;

        @Schema(description = "지역. 이 회차에 내 테이블이 있으면 테이블 지역, 없으면 null(희망 지역은 candidateRegions)",
                example = "강남/신논현", nullable = true)
        private String region;

        // 회차는 지역에 묶이지 않는다(모든 회차에 4개 지역이 열린다). 지역은 앉은 테이블에서만 정해진다. (KAN-430)
        static SessionInfo from(GatheringSession session, DiningTable table) {
            return SessionInfo.builder()
                    .id(session.getId())
                    .eventDate(session.getEventDate())
                    .startTime(session.getStartTime())
                    .region(table != null && table.getSession().getId().equals(session.getId())
                            ? table.getDisplayRegion() : null)
                    .build();
        }
    }

    /** 테이블 요약 (dining_tables 기준). */
    @Getter
    @Builder
    public static class TableInfo {
        @Schema(description = "테이블 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        private UUID id;

        @Schema(description = "테이블 상태 (PROPOSED: 확정 유예 중, CONFIRMED: 확정)", example = "CONFIRMED")
        private DiningTableStatus status;

        @Schema(description = "자동 확정 예정 시각(확정 유예 창 끝). 유예 없이 만든 테이블이면 null", example = "2026-10-08T21:00:00")
        private LocalDateTime confirmAt;

        static TableInfo from(DiningTable table) {
            return TableInfo.builder()
                    .id(table.getId())
                    .status(table.getStatus())
                    .confirmAt(table.getConfirmAt())
                    .build();
        }
    }
}
