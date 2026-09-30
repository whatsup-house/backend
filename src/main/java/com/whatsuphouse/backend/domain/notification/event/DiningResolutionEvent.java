package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 우연한 식탁 매칭 실패 해결 흐름(제안·회차 이동·환불)의 참가자 알림. type은 DINING_ALTERNATIVE_OFFERED 등. (KAN-347) */
@Getter
@RequiredArgsConstructor
public class DiningResolutionEvent {
    private final Application application;
    private final NotificationType type;
}
