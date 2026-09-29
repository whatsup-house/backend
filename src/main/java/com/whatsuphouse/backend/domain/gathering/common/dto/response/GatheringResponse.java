package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.location.entity.Location;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class GatheringResponse {

    private UUID id;
    private String title;
    private String description;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer price;
    private int maxAttendees;
    private GatheringStatus status;
    private String thumbnailUrl;
    private LocationSummary location;
    // 게더링 등록일 — 목록 정렬(최신순/오래된순)용. (KAN-295)
    private LocalDateTime createdAt;
    private List<String> tags;

    // 회차 하나를 기존 게더링 목록 항목 형태로 내려준다. id는 회차 ID. (KAN-337)
    public static GatheringResponse from(GatheringSession session) {
        Gathering gathering = session.getGathering();
        LocationSummary locationSummary = null;
        Location location = session.getLocation();
        if (location != null) {
            locationSummary = LocationSummary.builder()
                    .id(location.getId())
                    .name(location.getName())
                    .build();
        }

        return GatheringResponse.builder()
                .id(session.getId())
                .title(gathering.getTitle())
                .description(gathering.getDescription())
                .eventDate(session.getEventDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .price(session.getEffectivePrice())
                .maxAttendees(session.getMaxAttendees())
                .status(session.getEffectiveStatus())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .location(locationSummary)
                .createdAt(session.getCreatedAt())
                .tags(gathering.getTags())
                .build();
    }

    @Getter
    @Builder
    public static class LocationSummary {
        private UUID id;
        private String name;
    }
}
