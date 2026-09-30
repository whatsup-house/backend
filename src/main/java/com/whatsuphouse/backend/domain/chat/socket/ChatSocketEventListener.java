package com.whatsuphouse.backend.domain.chat.socket;

import com.whatsuphouse.backend.domain.chat.dto.response.ChatSocketEventResponse.Kind;
import com.whatsuphouse.backend.domain.chat.event.ChatMemberChangedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageDeletedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageReadEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageUpdatedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatNoticeChangedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatReactionChangedEvent;
import com.whatsuphouse.backend.domain.chat.service.ChatSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 채팅 코어 도메인 이벤트 → STOMP 브로드캐스트. 커밋된 변경만 내보낸다(롤백되면 전송 없음). */
@Component
@RequiredArgsConstructor
public class ChatSocketEventListener {

    private final ChatSocketService chatSocketService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        chatSocketService.broadcastMessage(Kind.MESSAGE_CREATED, event.getRoomId(), event.getMessageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageUpdated(ChatMessageUpdatedEvent event) {
        chatSocketService.broadcastMessage(Kind.MESSAGE_UPDATED, event.getRoomId(), event.getMessageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(ChatMessageDeletedEvent event) {
        chatSocketService.broadcastMessage(Kind.MESSAGE_DELETED, event.getRoomId(), event.getMessageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReactionChanged(ChatReactionChangedEvent event) {
        chatSocketService.broadcastMessage(Kind.REACTION_CHANGED, event.getRoomId(), event.getMessageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticeChanged(ChatNoticeChangedEvent event) {
        chatSocketService.broadcastNotice(event.getRoomId(), event.getNoticeMessageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberChanged(ChatMemberChangedEvent event) {
        chatSocketService.broadcastMemberChanged(event.getRoomId(), event.getUserIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRead(ChatMessageReadEvent event) {
        chatSocketService.broadcastRead(event.getRoomId(), event.getUserId(), event.getMessageId());
    }
}
