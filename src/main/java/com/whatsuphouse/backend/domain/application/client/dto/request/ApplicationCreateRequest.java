package com.whatsuphouse.backend.domain.application.client.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** 모임 신청: 종류 + 희망 회차. 일반 모임은 회차 정확히 1개, 우연한 식탁은 1개 이상. (KAN-338) */
@Getter
@NoArgsConstructor
public class ApplicationCreateRequest extends ApplicationRequest {

    @Schema(description = "모임 종류 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    @NotNull
    private UUID gatheringId;

    @Schema(description = "희망 회차 ID 목록. 앞일수록 우선순위가 높다. 일반 모임은 1개, 우연한 식탁은 1개 이상",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    @NotEmpty
    @Size(max = 20)
    private List<@NotNull UUID> candidateSessionIds;
}
