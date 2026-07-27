package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ApplicationListResponse {

    private UUID id;
    private String bookingNumber;
    private ApplicationStatus status;
    private GatheringInfo gathering;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    public static class GatheringInfo {
        private UUID id;
        private String title;
        private String eventDate;
        private String thumbnailUrl;
    }

    public static ApplicationListResponse from(Application application) {
        return ApplicationListResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .status(application.getStatus())
                .gathering(GatheringInfo.builder()
                        .id(application.getGathering().getId())
                        .title(application.getGathering().getTitle())
                        .eventDate(application.getGathering().getEventDate().toString())
                        .thumbnailUrl(application.getGathering().getThumbnailUrl())
                        .build())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
