package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.bot.dto.request.BotReplyRequest;
import com.whatsuphouse.backend.domain.bot.entity.BotDispatch;
import com.whatsuphouse.backend.domain.bot.enums.BotDispatchStatus;
import com.whatsuphouse.backend.domain.bot.repository.BotDispatchRepository;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * 그록봇 답변 수신(POST /api/bot/replies, KAN-406). 봇이 할 수 있는 건 답변 게시와 담당자 이관 표시뿐.
 * dispatch 행을 잠근 한 트랜잭션에서 처리해 같은 eventId 동시 호출에도 메시지는 한 개.
 */
@Service
@RequiredArgsConstructor
public class BotReplyService {

    static final String DEFAULT_HANDOFF_REASON = "AI 이관";

    private final BotDispatchRepository botDispatchRepository;
    private final BotDispatchService botDispatchService;
    private final ChatService chatService;

    @Value("${bot.max-text-length:2000}")
    private int maxTextLength;

    /**
     * 검증 순서: Idempotency-Key≠eventId 422 → dispatch 없음 404 → 방·메시지 불일치 403 → 이미 답함 409
     * → 같은 방에 더 새 dispatch 409(오래된 답변 폐기) → SENT 아님 409 → 담당자 응대 중(botPaused) 409
     * → text 비었거나 길이 초과 422.
     */
    @Transactional
    public void reply(String idempotencyKey, BotReplyRequest request) {
        if (!request.getEventId().toString().equalsIgnoreCase(idempotencyKey)) {
            throw new CustomException(ErrorCode.BOT_IDEMPOTENCY_KEY_MISMATCH);
        }
        BotDispatch dispatch = botDispatchRepository.findByEventIdForUpdate(request.getEventId())
                .orElseThrow(() -> new CustomException(ErrorCode.BOT_DISPATCH_NOT_FOUND));
        if (!dispatch.getConversationId().equals(request.getConversationId())
                || !dispatch.getMessageId().equals(request.getInReplyToMessageId())) {
            throw new CustomException(ErrorCode.BOT_REPLY_FORBIDDEN);
        }
        if (dispatch.isAnswered()) {
            throw new CustomException(ErrorCode.BOT_REPLY_ALREADY_HANDLED);
        }
        if (botDispatchRepository.existsByConversationIdAndCreatedAtAfter(dispatch.getConversationId(), dispatch.getCreatedAt())) {
            throw new CustomException(ErrorCode.BOT_DISPATCH_STALE);
        }
        if (dispatch.getStatus() != BotDispatchStatus.SENT) {
            throw new CustomException(ErrorCode.BOT_DISPATCH_NOT_SENT);
        }
        if (chatService.isBotPaused(dispatch.getConversationId())) {
            throw new CustomException(ErrorCode.BOT_ROOM_PAUSED);
        }
        String text = request.getText();
        if (!StringUtils.hasText(text) || text.codePointCount(0, text.length()) > maxTextLength) {
            throw new CustomException(ErrorCode.BOT_REPLY_INVALID_TEXT);
        }

        // UTF-8 모드: < > & " ' 다섯 개만 엔티티로 바꾼다(…·“” 같은 문자는 그대로). FE가 aiGenerated 메시지에서 되돌린다.
        chatService.postBotMessage(dispatch.getConversationId(), HtmlUtils.htmlEscape(text.strip(), "UTF-8"));
        if (request.isHandoff()) {
            String reason = StringUtils.hasText(request.getHandoffReason())
                    ? request.getHandoffReason().strip() : DEFAULT_HANDOFF_REASON;
            dispatch.markHandedOff(reason);
            botDispatchService.handOff(dispatch, reason, false);
        } else {
            dispatch.markReplied();
        }
    }
}
