package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.location.entity.Location;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class GatheringDetailResponse {

    private UUID id;
    private String title;
    private String description;
    private List<String> howToRun;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer price;
    private int maxAttendees;
    private GatheringStatus status;
    private GatheringType gatheringType;   // REGULAR / RANDOM_TABLE(우연한 식탁)
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
                    .naverMapUrl(location.getNaverMapUrl())
                    .kakaoMapUrl(location.getKakaoMapUrl())
                    .build();
        }

        return GatheringDetailResponse.builder()
                .id(gathering.getId())
                .title(gathering.getTitle())
                .description(gathering.getDescription())
                .howToRun(gathering.getHowToRun())
                .eventDate(gathering.getEventDate())
                .startTime(gathering.getStartTime())
                .endTime(gathering.getEndTime())
                .price(gathering.getPrice())
                .maxAttendees(gathering.getMaxAttendees())
                .status(gathering.getEffectiveStatus())
                .gatheringType(gathering.getGatheringType())
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
        private String naverMapUrl;
        private String kakaoMapUrl;
    }
}
