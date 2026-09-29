package com.whatsuphouse.backend.domain.chat.dto.response;

import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 방 목록 미리보기용 마지막 메시지(서명 URL·리액션 없이 가볍게) */
@Getter
@Builder
public class ChatLastMessageResponse {

    private UUID id;

    @Schema(example = "TEXT")
    private ChatMessageType type;

    private UUID senderId;

    @Schema(description = "TEXT 본문. IMAGE·SYSTEM·삭제면 null", example = "안녕하세요!")
    private String content;

    private ChatSystemKind systemKind;

    private Map<String, Object> systemParams;

    private boolean isDeleted;

    private LocalDateTime createdAt;
}
