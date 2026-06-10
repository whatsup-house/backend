package com.whatsuphouse.backend.domain.matching.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MemberMoveRequest {
    @NotNull
    private UUID targetGroupId;
}
