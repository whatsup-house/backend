package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ApplicationResponse {

    private UUID id;
    private String bookingNumber;
    private UUID gatheringId;
    private ApplicationStatus status;
    private LocalDateTime createdAt;

    public static ApplicationResponse from(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                // 기존 API 호환: gatheringId는 회차 ID. (KAN-337)
                .gatheringId(application.getSession().getId())
                .status(application.getStatus())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
