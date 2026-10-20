package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 채팅 메시지 → 그록봇 대기열(KAN-406). 대상 판정은 BotDispatchService.enqueue(봇·관리자·시스템 메시지는 무시).
 * botExecutor는 단일 스레드라 같은 방 디바운스 갱신이 서로 겹치지 않는다.
 */
@Component
@RequiredArgsConstructor
public class BotDispatchListener {

    private final BotDispatchService botDispatchService;

    @Async("botExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        botDispatchService.enqueue(event.getMessageId());
    }
}
