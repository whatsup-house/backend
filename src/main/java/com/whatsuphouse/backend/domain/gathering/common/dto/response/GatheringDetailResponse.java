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
public class GatheringDetailResponse {

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
    private LocationDetail location;

    public static GatheringDetailResponse from(Gathering gathering) {
        LocationDetail locationDetail = null;
        Location location = gathering.getLocation();
        if (location != null) {
            locationDetail = LocationDetail.builder()
                    .id(location.getId())
                    .name(location.getName())
                    .address(location.getAddress())
                    .mapUrl(location.getMapUrl())
                    .build();
        }

        return GatheringDetailResponse.builder()
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
                .location(locationDetail)
                .build();
    }

    @Getter
    @Builder
    public static class LocationDetail {
        private UUID id;
        private String name;
        private String address;
        private String mapUrl;
    }
}
