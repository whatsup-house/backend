package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.event.DiningResolutionEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 우연한 식탁 해결 선택 흐름의 인앱 알림. 발행 트랜잭션과 함께 커밋·롤백된다(InAppNotificationEventListener와 같은 방식). (KAN-347)
 */
@Component
@RequiredArgsConstructor
public class DiningResolutionNotificationListener {

    private final UserNotificationService userNotificationService;

    @EventListener
    public void onDiningResolution(DiningResolutionEvent event) {
        Application application = event.getApplication();
        String title = application.getGathering().getTitle();
        switch (event.getType()) {
            case DINING_ALTERNATIVE_OFFERED -> userNotificationService.create(application.getUser(), event.getType(),
                    "테이블 배정이 어려워요",
                    application.getMatchStatus() == MatchStatus.NO_MATCH
                            ? title + " 희망 회차에 맞는 테이블을 찾지 못했어요. 이용권 보관 또는 환불을 선택해 주세요."
                            : title + " 희망 회차에 맞는 테이블을 찾지 못했어요. 다른 회차 이동·이용권 보관·환불 중 선택해 주세요.",
                    NotificationLink.DINING_RESOLUTION);
            case DINING_TRANSFERRED -> userNotificationService.create(application.getUser(), event.getType(),
                    "회차를 옮겼어요", title + " 새 회차에서 매칭을 기다리고 있어요.", NotificationLink.APPLICATIONS);
            case DINING_REFUND_REQUESTED -> userNotificationService.create(application.getUser(), event.getType(),
                    "환불을 접수했어요", title + " 이용권 환불 요청이 접수되었어요.", NotificationLink.APPLICATIONS);
            case DINING_REFUND_COMPLETED -> userNotificationService.create(application.getUser(), event.getType(),
                    "환불이 완료됐어요", title + " 이용권 환불이 완료되었어요.", NotificationLink.APPLICATIONS);
            default -> {
            }
        }
    }
}
