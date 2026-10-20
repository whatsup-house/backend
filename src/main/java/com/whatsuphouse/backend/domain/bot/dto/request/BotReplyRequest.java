package com.whatsuphouse.backend.domain.bot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** 그록봇 답변. text가 비었거나 2000자 초과면 dispatch 검증 뒤 422(BotReplyService)라 여기서 막지 않는다. */
@Getter
@NoArgsConstructor
public class BotReplyRequest {

    @NotNull
    @Schema(description = "GET /api/bot/pending 항목의 eventId(= Idempotency-Key 헤더)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID eventId;

    @NotNull
    @Schema(description = "대기 문의 항목의 conversationId(문의방 ID)", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID conversationId;

    @NotNull
    @Schema(description = "대기 문의 항목의 messageId", example = "1b4e28ba-2fa1-11d2-883f-0016d3cca427")
    private UUID inReplyToMessageId;

    @NotNull
    @Pattern(regexp = "reply|handoff")
    @Schema(description = "reply: 답변 게시 / handoff: 답변 게시 + 담당자 이관", example = "reply")
    private String action;

    @Schema(description = "고객에게 보일 답변(1~2000자, HTML은 이스케이프해 저장)", example = "이용권은 3회 남아 있어요.")
    private String text;

    @Size(max = 500)
    @Schema(description = "handoff 사유(관리자만 봄)", example = "환불 요청")
    private String handoffReason;

    public boolean isHandoff() {
        return "handoff".equals(action);
    }
}
