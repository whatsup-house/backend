package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ChatMemberAddRequest {

    @NotEmpty
    @Schema(example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    private List<@NotNull UUID> userIds;
}
