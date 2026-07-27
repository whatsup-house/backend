package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ApplicationDeleteResponse {

    @Schema(description = "신청 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "신청 상태", example = "CANCELLED")
    private ApplicationStatus status;

    @Schema(description = "삭제 처리 시각", example = "2026-05-05T12:00:00")
    private LocalDateTime deletedAt;

    public static ApplicationDeleteResponse from(Application application) {
        return ApplicationDeleteResponse.builder()
                .id(application.getId())
                .status(application.getStatus())
                .deletedAt(application.getDeletedAt())
                .build();
    }
}
