package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.chat.service.ChatPushService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningResolutionEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 우연한 식탁 해결 선택 흐름의 인앱 알림. 발행 트랜잭션과 함께 커밋·롤백된다(InAppNotificationEventListener와 같은 방식). (KAN-347)
 * 매칭 실패(해결 선택 요청)는 커밋 뒤 같은 제목·본문을 웹 푸시로도 보낸다(구독 기기가 없으면 아무 일도 없고, 실패는 로그만). (KAN-438)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiningResolutionNotificationListener {

    static final String ALTERNATIVE_OFFERED_TITLE = "테이블 배정이 어려워요";
    // 신청 상태 카드의 해결 영역(FE NotificationBell의 DINING_RESOLUTION 라우트와 같다)
    static final String RESOLUTION_URL = "/mypage?tab=applications#dining-resolution";

    private final UserNotificationService userNotificationService;
    private final ChatPushService chatPushService;

    @EventListener
    public void onDiningResolution(DiningResolutionEvent event) {
        Application application = event.getApplication();
        String title = application.getGathering().getTitle();
        switch (event.getType()) {
            case DINING_ALTERNATIVE_OFFERED -> userNotificationService.create(application.getUser(), event.getType(),
                    ALTERNATIVE_OFFERED_TITLE, alternativeOfferedContent(application), NotificationLink.DINING_RESOLUTION);
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

    // 커밋 뒤라 롤백된 제안은 푸시하지 않는다.
    @TransactionalEventListener(fallbackExecution = true)
    public void pushAlternativeOffered(DiningResolutionEvent event) {
        Application application = event.getApplication();
        if (event.getType() != NotificationType.DINING_ALTERNATIVE_OFFERED || application.getUser() == null) {
            return;
        }
        try {
            chatPushService.pushToUsers(List.of(application.getUser().getId()), new ChatPushService.Payload(
                    ALTERNATIVE_OFFERED_TITLE, alternativeOfferedContent(application), RESOLUTION_URL));
        } catch (RuntimeException e) {
            log.warn("매칭 실패 푸시 실패: applicationId={}", application.getId(), e);
        }
    }

    static String alternativeOfferedContent(Application application) {
        String title = application.getGathering().getTitle();
        return application.getMatchStatus() == MatchStatus.NO_MATCH
                ? title + " 희망 회차에 맞는 테이블을 찾지 못했어요. 이용권 보관 또는 환불을 선택해 주세요."
                : title + " 희망 회차에 맞는 테이블을 찾지 못했어요. 다른 회차 이동·이용권 보관·환불 중 선택해 주세요.";
    }
}
