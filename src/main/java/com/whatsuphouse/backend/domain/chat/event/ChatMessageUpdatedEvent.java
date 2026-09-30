package com.whatsuphouse.backend.domain.chat.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** 메시지 수정. 실시간(STOMP)·웹 푸시 일감이 구독한다. 트랜잭션 안에서 발행되므로 구독 측은 AFTER_COMMIT으로 받는다. */
@Getter
@RequiredArgsConstructor
public class ChatMessageUpdatedEvent {
    private final UUID roomId;
    private final UUID messageId;
}
