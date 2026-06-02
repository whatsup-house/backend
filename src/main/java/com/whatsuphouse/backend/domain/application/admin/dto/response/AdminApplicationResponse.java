package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminApplicationResponse {

    private UUID id;
    private String bookingNumber;
    private String name;
    private String phone;
    private ApplicationStatus status;
    private UUID gatheringId;
    private UUID userId;
    private LocalDateTime createdAt;
    private List<AnswerView> answers;

    public static AdminApplicationResponse from(Application application) {
        return from(application, null);
    }

    public static AdminApplicationResponse from(Application application, List<AnswerView> answers) {
        return AdminApplicationResponse.builder()
                .id(application.getId())
                .bookingNumber(application.getBookingNumber())
                .name(application.getName())
                .phone(application.getPhone())
                .status(application.getStatus())
                .gatheringId(application.getGathering().getId())
                .userId(application.getUser() != null ? application.getUser().getId() : null)
                .createdAt(application.getCreatedAt())
                .answers(answers)
                .build();
    }
}
