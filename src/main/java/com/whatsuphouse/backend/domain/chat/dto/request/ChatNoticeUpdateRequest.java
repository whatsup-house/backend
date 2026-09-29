package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ChatNoticeUpdateRequest {

    @Schema(description = "공지로 고정할 메시지 ID. null이면 공지 해제", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
    private UUID messageId;
}
