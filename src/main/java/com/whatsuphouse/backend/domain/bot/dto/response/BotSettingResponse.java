package com.whatsuphouse.backend.domain.bot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BotSettingResponse {

    @Schema(description = "관리자 전역 토글", example = "true")
    private boolean enabled;

    @Schema(description = "실제 동작 여부(토글 + 서버 마스터 스위치·웹훅·토큰 설정)", example = "false")
    private boolean active;
}
