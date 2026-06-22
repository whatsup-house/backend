package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.notification.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 수신해 알림 발송으로 연결하는 리스너.
 *
 * [왜 @TransactionalEventListener인가?]
 * @EventListener는 이벤트 발행 시점(트랜잭션 도중)에 즉시 실행됩니다.
 * @TransactionalEventListener(phase = AFTER_COMMIT)는 트랜잭션이 커밋된 후에만
 * 실행되므로, DB 저장이 실패해 롤백되는 경우 이메일이 발송되지 않습니다.
 * 즉 "DB에는 없는데 이메일만 간" 상황을 방지합니다.
 *
 * [이벤트 발행처]
 * - WelcomeEvent          → AuthService.register()
 * - ApplicationPendingEvent   → ApplicationService.applyInternal()
 * - ApplicationConfirmedEvent → AdminApplicationService.changeStatus(CONFIRMED)
 * - ApplicationCancelledEvent → ApplicationService.cancel(), AdminApplicationService.deleteApplication()
 * - ApplicationAttendedEvent  → AdminApplicationService.changeStatus(ATTENDED)
 * - GatheringCancelledEvent   → AdminGatheringService.changeStatus(CANCELLED)
 *
 * [NotificationService 주입]
 * NotificationService 인터페이스만 의존하므로, 구현체(Email/SMS 등)가 교체되어도
 * 이 클래스는 변경하지 않아도 됩니다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * 회원가입 완료 이벤트 처리.
     * 트랜잭션 커밋 후 환영 이메일을 발송합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWelcome(WelcomeEvent event) {
        notificationService.sendWelcome(event.getUser());
    }

    /**
     * 신청 접수(PENDING) 이벤트 처리 (FR-NTF-01, 02).
     * 트랜잭션 커밋 후 신청 확인 이메일을 발송합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationPending(ApplicationPendingEvent event) {
        notificationService.sendApplicationPending(event.getApplication());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationApproved(ApplicationApprovedEvent event) {
        notificationService.sendApplicationApproved(event.getApplication());
    }

    /**
     * 신청 확정(CONFIRMED) 이벤트 처리 (FR-NTF-03).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationConfirmed(ApplicationConfirmedEvent event) {
        notificationService.sendApplicationConfirmed(event.getApplication());
    }

    /**
     * 입금 완료 이벤트 처리 (KAN-242).
     * 관리자가 입금을 확인(체크)하면 신청자에게 입금 완료 안내를 발송합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationPaymentConfirmed(ApplicationPaymentConfirmedEvent event) {
        notificationService.sendPaymentConfirmed(event.getApplication());
    }

    /**
     * 신청 취소(CANCELLED) 이벤트 처리 (FR-NTF-04).
     * 사용자 직접 취소와 관리자 삭제 모두 이 이벤트를 발행합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationCancelled(ApplicationCancelledEvent event) {
        notificationService.sendApplicationCancelled(event.getApplication());
    }

    /**
     * 참석 처리(ATTENDED) 이벤트 처리 (FR-NTF-05).
     * 이벤트에 마일리지 정보가 포함되어 있어 적립 내역을 이메일에 담습니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationAttended(ApplicationAttendedEvent event) {
        notificationService.sendApplicationAttended(
                event.getApplication(),
                event.getMileageEarned(),
                event.getMileageBalance());
    }

    /**
     * 모임 취소 이벤트 처리 (FR-NTF-06).
     * 이벤트에 PENDING/CONFIRMED 신청자 목록이 포함되어 있어 일괄 발송합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGatheringCancelled(GatheringCancelledEvent event) {
        notificationService.sendGatheringCancelled(
                event.getGathering(),
                event.getApplications());
    }
}
