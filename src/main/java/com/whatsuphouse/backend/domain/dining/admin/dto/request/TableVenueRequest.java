package com.whatsuphouse.backend.domain.dining.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TableVenueRequest {

    @NotNull
    @Schema(description = "배정할 식당 ID(회차 식당 풀에 있어야 함)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID venueId;
}
