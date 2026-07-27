package com.whatsuphouse.backend.domain.carousel.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarouselSlideActiveRequest {

    @NotNull
    @Schema(example = "true")
    private Boolean isActive;
}
