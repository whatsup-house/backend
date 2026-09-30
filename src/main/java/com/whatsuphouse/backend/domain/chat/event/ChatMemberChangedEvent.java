package com.whatsuphouse.backend.domain.chat.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

/** 멤버 추가(재초대 포함)·내보내기·나가기. 실시간(STOMP)·웹 푸시 일감이 구독한다. 트랜잭션 안에서 발행되므로 구독 측은 AFTER_COMMIT으로 받는다. */
@Getter
@RequiredArgsConstructor
public class ChatMemberChangedEvent {
    private final UUID roomId;
    private final List<UUID> userIds;
}
