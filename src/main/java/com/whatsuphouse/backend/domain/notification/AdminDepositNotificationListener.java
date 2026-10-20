package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.chat.service.ChatPushService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.AdminDepositNotifiedEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;

/**
 * 입금 알림 대사 결과 → 활성 관리자 전원에게 인앱 알림(+ 웹 푸시 구독 기기). 발행 트랜잭션 커밋 후 관리자마다 새 트랜잭션으로 적재한다.
 * 알림 실패는 로그만 남긴다: 정산은 이미 끝났고, 같은 내용이 입금 관리 화면(/admin/payments)에 있다. (KAN-416)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDepositNotificationListener {

    static final String PAYMENTS_URL = "/admin/payments";

    private final UserNotificationService userNotificationService;
    private final UserService userService;
    private final ChatPushService chatPushService;
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener(fallbackExecution = true)
    public void onDepositNotified(AdminDepositNotifiedEvent event) {
        try {
            List<User> admins = userService.findUsersByIds(userService.listActiveAdminIds()).values().stream()
                    .filter(user -> !user.isWithdrawn())
                    .toList();
            NotificationType type = event.isMatched()
                    ? NotificationType.ADMIN_DEPOSIT_MATCHED : NotificationType.ADMIN_DEPOSIT_EXCEPTION;
            String title = title(event);
            String content = event.isMatched()
                    ? "결제 대기 건과 연결해 결제 완료로 처리했어요."
                    : "자동으로 연결하지 못했어요. 입금 관리 예외함에서 확인해 주세요.";
            admins.forEach(admin -> create(admin, type, title, content, event));
            chatPushService.pushToUsers(admins.stream().map(User::getId).toList(),
                    new ChatPushService.Payload(title, content, PAYMENTS_URL));
        } catch (RuntimeException e) {
            log.warn("관리자 입금 알림 실패: depositNotificationId={}", event.getDepositNotificationId(), e);
        }
    }

    // 예: "입금 확인: 한지형 9,900원 — 자동 처리됨", "입금 확인 필요: 한지형 9,900원 — 예외함"
    static String title(AdminDepositNotifiedEvent event) {
        String who = event.getPayerName() != null && event.getAmount() != null
                ? event.getPayerName() + " " + String.format(Locale.ROOT, "%,d원", event.getAmount())
                : "읽지 못한 입금 알림";
        if (!event.isMatched()) {
            return "입금 확인 필요: " + who + " — 예외함";
        }
        return "입금 확인: " + who + (event.isManual() ? " — 수동 연결됨" : " — 자동 처리됨");
    }

    private void create(User admin, NotificationType type, String title, String content, AdminDepositNotifiedEvent event) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> userNotificationService.create(admin, type, title, content,
                    NotificationLink.ADMIN_PAYMENTS, event.getDepositNotificationId()));
        } catch (RuntimeException e) {
            log.warn("관리자 입금 알림 적재 실패: type={}, adminId={}", type, admin.getId(), e);
        }
    }
}
