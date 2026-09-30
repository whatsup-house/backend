package com.whatsuphouse.backend.domain.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

/** 우연한 식탁 테이블 확정. 확정 파이프라인(KAN-346)이 확정 트랜잭션 뒤 별도 트랜잭션에서 발행한다. */
@Getter
@RequiredArgsConstructor
public class DiningTableConfirmedEvent {
    private final UUID tableId;
    private final UUID sessionId;
    private final List<UUID> memberUserIds;
}
