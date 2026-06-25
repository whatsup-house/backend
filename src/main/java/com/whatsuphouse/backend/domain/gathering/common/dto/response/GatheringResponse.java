package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
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

    public static GatheringResponse from(Gathering gathering) {
        LocationSummary locationSummary = null;
        Location location = gathering.getLocation();
        if (location != null) {
            locationSummary = LocationSummary.builder()
                    .id(location.getId())
                    .name(location.getName())
                    .build();
        }

        return GatheringResponse.builder()
                .id(gathering.getId())
                .title(gathering.getTitle())
                .description(gathering.getDescription())
                .eventDate(gathering.getEventDate())
                .startTime(gathering.getStartTime())
                .endTime(gathering.getEndTime())
                .price(gathering.getPrice())
                .maxAttendees(gathering.getMaxAttendees())
                .status(gathering.getEffectiveStatus())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .location(locationSummary)
                .createdAt(gathering.getCreatedAt())
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
