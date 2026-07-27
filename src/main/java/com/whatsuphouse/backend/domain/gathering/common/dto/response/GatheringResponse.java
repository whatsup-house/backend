package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.location.entity.Location;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
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
                .status(gathering.getStatus())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .location(locationSummary)
                .build();
    }

    @Getter
    @Builder
    public static class LocationSummary {
        private UUID id;
        private String name;
    }
}
