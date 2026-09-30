package com.whatsuphouse.backend.domain.dining.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 회차 식당 풀 일괄 설정의 한 항목. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SessionVenueRequest {

    @NotNull
    @Schema(description = "식당 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID venueId;

    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "이 회차에서 받을 수 있는 테이블 수", example = "3")
    private Integer capacityTables;
}
