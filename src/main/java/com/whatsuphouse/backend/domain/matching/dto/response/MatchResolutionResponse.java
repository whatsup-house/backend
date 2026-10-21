package com.whatsuphouse.backend.domain.matching.dto.response;

import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.matching.entity.MatchResolution;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionChoice;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** 매칭 실패 해결 선택. GET /api/dining/resolutions/{id} (KAN-347) */
@Getter
@Builder
public class MatchResolutionResponse {

    @Schema(description = "해결 선택 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID applicationId;

    @Schema(description = "옮길 수 있는 대체 회차(제안 회차 중 아직 모집 중인 회차). 비어 있으면 KEEP_TICKET/REFUND만 가능")
    private List<SessionView> offeredSessions;

    @Schema(description = "신청자가 고르지 않은 지역 라벨, 지역 선언 순. TRANSFER 때 regions로 희망 지역을 다시 고를 수 있다",
            example = "[\"잠실\", \"을지로/종로\"]")
    private List<String> offeredRegions;

    @Schema(description = "선택. 응답 전이면 null. 기한 만료(EXPIRED)는 KEEP_TICKET으로 자동 처리", example = "TRANSFER", nullable = true)
    private ResolutionChoice choice;

    @Schema(description = "응답 기한. 지나면 이용권 보관으로 자동 처리", example = "2026-10-10T21:00:00")
    private LocalDateTime respondBy;

    @Schema(description = "상태", example = "OFFERED")
    private ResolutionStatus status;

    public static MatchResolutionResponse of(MatchResolution resolution, List<GatheringSession> offeredSessions) {
        return MatchResolutionResponse.builder()
                .id(resolution.getId())
                .applicationId(resolution.getApplication().getId())
                .offeredSessions(offeredSessions.stream().map(SessionView::from).toList())
                .offeredRegions(Arrays.stream(DiningRegion.values())
                        .filter(region -> !resolution.getApplication().getCandidateRegions().contains(region))
                        .map(DiningRegion::getLabel)
                        .toList())
                .choice(resolution.getChoice())
                .respondBy(resolution.getRespondBy())
                .status(resolution.getStatus())
                .build();
    }

    @Getter
    @Builder
    public static class SessionView {
        @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private UUID id;

        @Schema(description = "행사 날짜", example = "2026-10-17")
        private LocalDate eventDate;

        @Schema(description = "시작 시간", example = "19:00:00")
        private LocalTime startTime;

        @Schema(description = "종료 시간", example = "21:00:00")
        private LocalTime endTime;

        @Schema(description = "지역. 회차는 지역에 묶이지 않아 항상 null(이동 때 regions를 보내지 않으면 신청의 희망 지역이 유지된다)", nullable = true)
        private String region;

        static SessionView from(GatheringSession session) {
            return SessionView.builder()
                    .id(session.getId())
                    .eventDate(session.getEventDate())
                    .startTime(session.getStartTime())
                    .endTime(session.getEndTime())
                    .build();
        }
    }
}
