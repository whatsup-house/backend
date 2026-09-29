package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
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
    private List<String> tags;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer price;
    private int maxAttendees;
    private GatheringStatus status;
    private GatheringType gatheringType;   // REGULAR / RANDOM_TABLE(우연한 식탁)
    private String thumbnailUrl;
    private LocationDetail location;

    // 회차 상세. id는 회차 ID, 소개·썸네일 등은 종류 값. (KAN-337)
    public static GatheringDetailResponse from(GatheringSession session) {
        return from(session, session.getGathering().getTitle(), session.getGathering().getDescription());
    }

    // 로케일별 번역이 적용된 title/description을 받는 오버로드. (KAN-266)
    public static GatheringDetailResponse from(GatheringSession session, String title, String description) {
        Gathering gathering = session.getGathering();
        LocationDetail locationDetail = null;
        Location location = session.getLocation();
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
                .id(session.getId())
                .title(title)
                .description(description)
                .howToRun(gathering.getHowToRun())
                .tags(gathering.getTags())
                .eventDate(session.getEventDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .price(session.getEffectivePrice())
                .maxAttendees(session.getMaxAttendees())
                .status(session.getEffectiveStatus())
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
