package com.whatsuphouse.backend.domain.carousel.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarouselSlideOrderRequest {

    @NotNull
    @Schema(example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\", \"550e8400-e29b-41d4-a716-446655440000\"]")
    private List<UUID> slideIds;
}
