package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.chat.service.ChatPushService;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.service.NoticeLogService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningTableConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 우연한 식탁 테이블 확정 → 멤버마다 테이블 상세로 가는 인앱 알림. (KAN-346)
 * 발행 트랜잭션 안에서 적재한다(@EventListener). 적재가 실패하면 발행 측이 예외를 받아 예외함(NOTIFICATION)에 남긴다.
 * 커밋 뒤 같은 제목·본문을 웹 푸시로도 보낸다(구독 기기가 없으면 아무 일도 없고, 실패는 로그만). (KAN-438)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiningConfirmNotificationListener {

    static final String TITLE = "우연한 식탁 테이블이 확정되었어요";
    static final String CONTENT = "함께할 테이블이 정해졌어요. 테이블 상세에서 식당과 채팅방을 확인해 주세요.";

    private final UserNotificationService userNotificationService;
    private final UserService userService;
    private final ChatPushService chatPushService;
    private final NoticeLogService noticeLogService;

    @EventListener
    public void onDiningTableConfirmed(DiningTableConfirmedEvent event) {
        userService.findUsersByIds(event.getMemberUserIds()).values().stream()
                .filter(user -> !user.isWithdrawn())
                .forEach(user -> userNotificationService.create(
                        user,
                        NotificationType.DINING_CONFIRMED,
                        TITLE,
                        CONTENT,
                        NotificationLink.DINING_TABLE,
                        event.getTableId()));
    }

    // 커밋 뒤라 롤백된 확정 알림은 푸시하지 않는다. 대상(memberUserIds)은 발행 측이 탈퇴 회원을 뺀 목록이다.
    // 발송 이력(NOTIFICATION/PUSH)은 비동기 발송을 넘긴 시점에 남긴다. 기기별 실패는 WebPushSender 로그로만 남는다.
    @TransactionalEventListener(fallbackExecution = true)
    public void pushDiningTableConfirmed(DiningTableConfirmedEvent event) {
        if (!chatPushService.isEnabled() || event.getMemberUserIds().isEmpty()) {
            return;
        }
        try {
            chatPushService.pushToUsers(event.getMemberUserIds(),
                    new ChatPushService.Payload(TITLE, CONTENT, tableUrl(event.getTableId())));
            noticeLogService.recordAfterCommit(event.getTableId(), NoticeKind.NOTIFICATION, NoticeChannel.PUSH, null);
        } catch (RuntimeException e) {
            log.warn("테이블 확정 푸시 실패: tableId={}", event.getTableId(), e);
            noticeLogService.recordAfterCommit(event.getTableId(), NoticeKind.NOTIFICATION, NoticeChannel.PUSH, e);
        }
    }

    /** 테이블 상세 화면 경로(FE NotificationBell의 DINING_TABLE 라우트와 같다). 푸시 클릭 시 이동한다. */
    static String tableUrl(UUID tableId) {
        return "/dining/tables/" + tableId;
    }
}
