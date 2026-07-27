package com.whatsuphouse.backend.domain.gathering.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class GatheringCurationOrderRequest {

    @Schema(description = "순서 변경할 큐레이션 게더링 ID 목록 (앞에서부터 1위)", example = "[\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\", \"b2c3d4e5-f6a7-8901-bcde-f12345678901\"]")
    @NotNull
    private List<UUID> gatheringIds;
}
