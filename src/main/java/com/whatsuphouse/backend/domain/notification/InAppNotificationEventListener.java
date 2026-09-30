package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationApprovedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationAttendedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 도메인 이벤트를 인앱 알림으로 적재하는 리스너. (KAN-263)
 *
 * 이메일 발송용 {@link NotificationEventListener}는 AFTER_COMMIT으로 발송하지만,
 * 인앱 알림은 DB 적재라 발행 트랜잭션과 같은 트랜잭션에서 처리(@EventListener)해
 * 발행이 롤백되면 알림도 함께 롤백되도록 한다.
 */
@Component
@RequiredArgsConstructor
public class InAppNotificationEventListener {

    private final UserNotificationService userNotificationService;

    /** 참가 확정 → 마이페이지 신청 내역으로 이동하는 알림. 우연한 식탁은 매칭 대기 알림(DINING_WAITING, KAN-349)으로 대신한다. */
    @EventListener
    public void onApplicationConfirmed(ApplicationConfirmedEvent event) {
        Application application = event.getApplication();
        if (application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE) {
            return;
        }
        userNotificationService.create(
                application.getUser(),
                NotificationType.PARTICIPATION_CONFIRMED,
                "참가가 확정되었어요",
                application.getGathering().getTitle() + " 참가가 확정되었습니다.",
                NotificationLink.APPLICATIONS);
    }

    /**
     * 우연한 식탁 결제 대기 → 이용권 구매로 이동하는 알림. (KAN-342)
     * ApplicationApprovedEvent는 우연한 식탁 신청이 이용권 부족으로 PAYMENT_PENDING이 될 때만 발행된다
     * (신청 즉시, 관리자 승인 후 모두).
     */
    @EventListener
    public void onApplicationApproved(ApplicationApprovedEvent event) {
        Application application = event.getApplication();
        userNotificationService.create(
                application.getUser(),
                NotificationType.DINING_PAYMENT_PENDING,
                "이용권 결제가 필요해요",
                application.getGathering().getTitle() + " 신청을 마치려면 이용권을 구매해 주세요.",
                NotificationLink.TICKET_PURCHASE,
                application.getId());
    }

    /** 참석 처리 시 마일리지 적립 → 마일리지 현황으로 이동하는 알림. */
    @EventListener
    public void onApplicationAttended(ApplicationAttendedEvent event) {
        if (event.getMileageEarned() <= 0) {
            return;
        }
        Application application = event.getApplication();
        userNotificationService.create(
                application.getUser(),
                NotificationType.MILEAGE_EARNED,
                "마일리지가 적립되었어요",
                event.getMileageEarned() + "M가 적립되어 현재 " + event.getMileageBalance() + "M이에요.",
                NotificationLink.MILEAGE);
    }
}
