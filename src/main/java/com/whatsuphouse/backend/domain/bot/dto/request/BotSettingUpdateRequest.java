package com.whatsuphouse.backend.domain.bot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BotSettingUpdateRequest {

    @NotNull
    @Schema(description = "그록봇 자동응답 전역 토글", example = "true")
    private Boolean enabled;
}
