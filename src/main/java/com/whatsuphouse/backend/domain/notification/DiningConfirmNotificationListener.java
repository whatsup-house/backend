package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningTableConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 우연한 식탁 테이블 확정 → 멤버마다 테이블 상세로 가는 인앱 알림. (KAN-346)
 * 발행 트랜잭션 안에서 적재한다(@EventListener). 적재가 실패하면 발행 측이 예외를 받아 예외함(NOTIFICATION)에 남긴다.
 */
@Component
@RequiredArgsConstructor
public class DiningConfirmNotificationListener {

    private final UserNotificationService userNotificationService;
    private final UserService userService;

    @EventListener
    public void onDiningTableConfirmed(DiningTableConfirmedEvent event) {
        userService.findUsersByIds(event.getMemberUserIds()).values().stream()
                .filter(user -> !user.isWithdrawn())
                .forEach(user -> userNotificationService.create(
                        user,
                        NotificationType.DINING_CONFIRMED,
                        "우연한 식탁 테이블이 확정되었어요",
                        "함께할 테이블이 정해졌어요. 테이블 상세에서 식당과 채팅방을 확인해 주세요.",
                        NotificationLink.DINING_TABLE,
                        event.getTableId()));
    }
}
