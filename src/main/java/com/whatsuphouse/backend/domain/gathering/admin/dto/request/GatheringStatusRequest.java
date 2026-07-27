package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class GatheringStatusRequest {

    @Schema(example = "CLOSED")
    @NotNull
    private GatheringStatus status;
}
