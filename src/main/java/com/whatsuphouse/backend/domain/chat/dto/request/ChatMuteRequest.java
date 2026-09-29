package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMuteRequest {

    @NotBlank
    @Size(max = 1000)
    @Schema(example = "반복적인 도배")
    private String reason;
}
