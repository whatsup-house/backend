package com.whatsuphouse.backend.domain.chat.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** 메시지 소프트 삭제. 실시간(STOMP)·웹 푸시 일감이 구독한다. 트랜잭션 안에서 발행되므로 구독 측은 AFTER_COMMIT으로 받는다. */
@Getter
@RequiredArgsConstructor
public class ChatMessageDeletedEvent {
    private final UUID roomId;
    private final UUID messageId;
}
