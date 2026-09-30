package com.whatsuphouse.backend.domain.chat.dto.response;

import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChatReportResponse {

    private UUID id;

    private UUID roomId;

    private UUID messageId;

    private UUID reporterId;

    @Schema(nullable = true)
    private String reporterNickname;

    private String reason;

    private ChatReportStatus status;

    private LocalDateTime createdAt;

    private ChatMessageType messageType;

    private UUID messageSenderId;

    @Schema(nullable = true)
    private String messageSenderNickname;

    @Schema(description = "검토용 TEXT 원문(삭제된 메시지 포함). IMAGE·SYSTEM은 null")
    private String messageContent;

    private boolean isMessageDeleted;
}
