package com.whatsuphouse.backend.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatSocketTokenResponse {

    @Schema(description = "STOMP CONNECT 의 Authorization: Bearer 헤더에 넣을 단기 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "토큰 수명(초)", example = "120")
    private long expiresIn;
}
