package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
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

    // ── 우연한 식탁(RANDOM_TABLE) 회차 전용. 그 외 타입에 값이 오면 400. 비우면 매칭 규칙 기본값. ──

    @Schema(example = "2026-10-08T21:00:00", description = "매칭 실행 예정 시각 (우연한 식탁 전용)")
    private LocalDateTime matchRunAt;

    @Schema(example = "120", description = "자동 확정 유예(분). 0이면 즉시 확정 (우연한 식탁 전용)")
    @Min(0)
    @Max(10080)
    private Integer autoConfirmGraceMinutes;

    @Schema(example = "4", description = "테이블 최소 인원 (우연한 식탁 전용)")
    @Min(2)
    @Max(8)
    private Integer tableSizeMin;

    @Schema(example = "6", description = "테이블 최대 인원 (우연한 식탁 전용)")
    @Min(2)
    @Max(8)
    private Integer tableSizeMax;

    @Schema(example = "0.35", description = "그룹 최소 점수. 미달 테이블은 해체 후 재배치 (우연한 식탁 전용)")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 4)
    private BigDecimal minGroupScore;

    @Schema(example = "8", description = "테이블 내 최대 나이 차(출생연도 기준) (우연한 식탁 전용)")
    @Min(0)
    @Max(100)
    private Integer maxAgeGap;

    protected void copyFrom(GatheringSessionRequest other) {
        this.eventDate = other.eventDate;
        this.startTime = other.startTime;
        this.endTime = other.endTime;
        this.locationId = other.locationId;
        this.maxAttendees = other.maxAttendees;
        this.priceOverride = other.priceOverride;
        this.applyDeadlineAt = other.applyDeadlineAt;
        this.matchRunAt = other.matchRunAt;
        this.autoConfirmGraceMinutes = other.autoConfirmGraceMinutes;
        this.tableSizeMin = other.tableSizeMin;
        this.tableSizeMax = other.tableSizeMax;
        this.minGroupScore = other.minGroupScore;
        this.maxAgeGap = other.maxAgeGap;
    }
}
