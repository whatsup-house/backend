package com.whatsuphouse.backend.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatImageUploadResponse {

    @Schema(description = "IMAGE 메시지 content로 보낼 경로(chat-images 버킷)",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6/550e8400-e29b-41d4-a716-446655440000.webp")
    private String path;

    @Schema(description = "미리보기 서명 URL(1시간 유효). 서명 실패 시 null")
    private String url;
}
