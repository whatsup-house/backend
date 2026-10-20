package com.whatsuphouse.backend.domain.bot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class BotPendingResponse {

    @Schema(description = "이번 호출로 가져간(lease, 5분) 대기 문의. 항목: eventId, conversationId, messageId, "
            + "customer{id, displayName}, message{text, createdAt}, history[], context{applications[], tickets{}, depositRequired}, faq[]. "
            + "없으면 빈 배열")
    private final List<Map<String, Object>> items;
}
