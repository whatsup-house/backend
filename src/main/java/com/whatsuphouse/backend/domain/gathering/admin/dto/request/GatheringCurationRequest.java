package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class GatheringCurationRequest {

    @Schema(description = "큐레이션 노출 여부", example = "true")
    @NotNull
    private Boolean isCurated;
}
