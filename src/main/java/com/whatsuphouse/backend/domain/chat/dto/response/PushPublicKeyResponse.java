package com.whatsuphouse.backend.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PushPublicKeyResponse {

    @Schema(description = "VAPID 공개키(base64url). PushManager.subscribe의 applicationServerKey로 쓴다.",
            example = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U")
    private String publicKey;
}
