package com.whatsuphouse.backend.domain.chat.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** 읽음 위치 전진(역행·같은 위치는 발행 안 함). 실시간(STOMP) 일감이 READ로 브로드캐스트한다. 트랜잭션 안에서 발행되므로 구독 측은 AFTER_COMMIT으로 받는다. */
@Getter
@RequiredArgsConstructor
public class ChatMessageReadEvent {
    private final UUID roomId;
    private final UUID userId;
    private final UUID messageId;
}
