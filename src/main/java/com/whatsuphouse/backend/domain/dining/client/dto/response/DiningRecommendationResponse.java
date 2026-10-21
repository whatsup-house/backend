package com.whatsuphouse.backend.domain.dining.client.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** 다음 추천 회차 한 건. GET /api/dining/me/recommendations (KAN-407) */
@Getter
@Builder
public class DiningRecommendationResponse {

    @Schema(description = "모임 종류 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID gatheringId;

    @Schema(description = "회차 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID sessionId;

    @Schema(description = "모임 제목", example = "우연한 식탁")
    private String title;

    @Schema(description = "행사 날짜", example = "2026-10-24")
    private LocalDate eventDate;

    @Schema(description = "시작 시간", example = "19:00", nullable = true)
    private LocalTime startTime;

    @Schema(description = "지역. 회차는 지역에 묶이지 않아 항상 null(신청할 때 희망 지역을 고른다)", nullable = true)
    private String region;

    public static DiningRecommendationResponse from(GatheringSession session) {
        return DiningRecommendationResponse.builder()
                .gatheringId(session.getGathering().getId())
                .sessionId(session.getId())
                .title(session.getGathering().getTitle())
                .eventDate(session.getEventDate())
                .startTime(session.getStartTime())
                .build();
    }
}
