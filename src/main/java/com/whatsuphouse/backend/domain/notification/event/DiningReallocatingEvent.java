package com.whatsuphouse.backend.domain.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** 우연한 식탁 신청이 매칭 실행에서 미배정돼 다음 희망 회차 대기(REALLOCATING)로 바뀜. sessionId는 방금 실행한 회차. (KAN-349) */
@Getter
@RequiredArgsConstructor
public class DiningReallocatingEvent {
    private final UUID applicationId;
    private final UUID sessionId;
    private final UUID userId;
}
