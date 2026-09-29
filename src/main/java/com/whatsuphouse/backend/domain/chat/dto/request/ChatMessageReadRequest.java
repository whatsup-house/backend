package com.whatsuphouse.backend.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** STOMP SEND /app/rooms/{id}/read 본문 */
@Getter
@NoArgsConstructor
public class ChatMessageReadRequest {

    @NotNull
    @Schema(description = "여기까지 읽음(같은 방 메시지)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID messageId;
}
