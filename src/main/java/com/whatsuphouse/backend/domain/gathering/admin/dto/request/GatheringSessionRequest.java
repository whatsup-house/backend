package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** 모임 회차 필드. 회차 수정 요청이자 반복 생성의 기준 회차(base). (KAN-338) */
@Getter
@SuperBuilder
@NoArgsConstructor
public class GatheringSessionRequest {

    @Schema(example = "2026-10-10")
    @NotNull
    private LocalDate eventDate;

    @Schema(example = "19:00:00")
    private LocalTime startTime;

    @Schema(example = "21:00:00")
    private LocalTime endTime;

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull
    private UUID locationId;

    @Schema(example = "10")
    @NotNull
    @Positive
    private Integer maxAttendees;

    @Schema(example = "20000", description = "이 회차만의 가격. 비우면 종류 기본 가격")
    @PositiveOrZero
    private Integer priceOverride;

    @Schema(example = "2026-10-09T23:59:00", description = "신청 마감 시각. 비우면 마감 없음")
    private LocalDateTime applyDeadlineAt;

    protected void copyFrom(GatheringSessionRequest other) {
        this.eventDate = other.eventDate;
        this.startTime = other.startTime;
        this.endTime = other.endTime;
        this.locationId = other.locationId;
        this.maxAttendees = other.maxAttendees;
        this.priceOverride = other.priceOverride;
        this.applyDeadlineAt = other.applyDeadlineAt;
    }
}
