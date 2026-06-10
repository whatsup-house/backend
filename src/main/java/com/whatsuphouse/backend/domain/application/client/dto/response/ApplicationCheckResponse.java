package com.whatsuphouse.backend.domain.application.client.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ApplicationCheckResponse {

    private UUID id;
    private String bookingNumber;
    private String name;
    private String phone;
    private ApplicationStatus status;
    private GatheringInfo gathering;
    private LocalDateTime createdAt;
    private List<AnswerView> answers;

    @Getter
    @Builder
    public static class GatheringInfo {
        private UUID id;
        private String title;
        private String eventDate;
        private String startTime;
    }

    public static ApplicationCheckResponse from(Application application, List<AnswerView> answers) {
        return ApplicationCheckResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .name(application.getName())
                .phone(application.getPhone())
                .status(application.getStatus())
                .gathering(GatheringInfo.builder()
                        .id(application.getGathering().getId())
                        .title(application.getGathering().getTitle())
                        .eventDate(application.getGathering().getEventDate().toString())
                        .startTime(application.getGathering().getStartTime() != null
                                ? application.getGathering().getStartTime().toString() : null)
                        .build())
                .createdAt(application.getCreatedAt())
                .answers(answers)
                .build();
    }
}
