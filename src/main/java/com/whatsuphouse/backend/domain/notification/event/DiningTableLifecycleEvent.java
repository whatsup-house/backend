package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

/** 확정 테이블 멤버에게 보내는 진행 알림. type은 DINING_REMINDER(24시간 전) 또는 DINING_FEEDBACK_REQUEST(종료 후). (KAN-349) */
@Getter
@RequiredArgsConstructor
public class DiningTableLifecycleEvent {
    private final NotificationType type;
    private final UUID tableId;
    private final UUID sessionId;
    private final List<UUID> memberUserIds;
}
