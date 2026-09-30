package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.DiningReallocatingEvent;
import com.whatsuphouse.backend.domain.notification.event.DiningSessionsCreatedEvent;
import com.whatsuphouse.backend.domain.notification.event.DiningTableLifecycleEvent;
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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 우연한 식탁 진행 알림(설계 2.8, KAN-349): 매칭 대기·재배치 대기·리마인드·피드백 요청·다음 모집.
 * 발행 트랜잭션이 커밋된 뒤 회원마다 새 트랜잭션으로 적재한다. 적재가 실패하면 최대 3회 시도하고, 그래도 안 되면 예외함(NOTIFICATION)에 남긴다.
 * 한 회원의 실패가 발행한 업무(확정·종료 처리 등)나 다른 회원 알림을 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiningLifecycleNotificationListener {

    static final int MAX_ATTEMPTS = 3;
    private static final int NEXT_SESSIONS_LOOKBACK_MONTHS = 6;

    private final UserNotificationService userNotificationService;
    private final UserService userService;
    private final DiningAttendanceService diningAttendanceService;
    private final ExceptionCaseService exceptionCaseService;
    private final PlatformTransactionManager transactionManager;

    /** 결제 완료(참가 확정)로 매칭 대기(WAITING)에 들어간 우연한 식탁 신청 → 내 신청(linkId = 신청 ID). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onApplicationConfirmed(ApplicationConfirmedEvent event) {
        Application application = event.getApplication();
        if (application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE
                || application.getMatchStatus() != MatchStatus.WAITING || application.getUser() == null) {
            return;
        }
        String title = application.getGathering().getTitle();
        notifyUsers(List.of(application.getUser().getId()), NotificationType.DINING_WAITING,
                "우연한 식탁 매칭을 기다리고 있어요",
                user -> title + " 결제가 완료됐어요. 테이블이 정해지면 바로 알려드릴게요.",
                NotificationLink.APPLICATIONS, application.getId(), new Target(null, null, application.getId()));
    }

    /** 미배정 → 다음 희망 회차 대기(REALLOCATING) → 내 신청(linkId = 신청 ID). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onReallocating(DiningReallocatingEvent event) {
        notifyUsers(List.of(event.getUserId()), NotificationType.DINING_REALLOCATING,
                "다음 희망 회차로 매칭을 이어가요",
                user -> "이번 회차에서는 테이블이 정해지지 않았어요. 다음 희망 회차 매칭에 자동으로 참여해요.",
                NotificationLink.APPLICATIONS, event.getApplicationId(),
                new Target(event.getSessionId(), null, event.getApplicationId()));
    }

    /** 리마인드(→ 테이블 상세)·피드백 요청(→ 피드백 작성). linkId = 테이블 ID. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onTableLifecycle(DiningTableLifecycleEvent event) {
        boolean isReminder = event.getType() == NotificationType.DINING_REMINDER;
        notifyUsers(event.getMemberUserIds(), event.getType(),
                isReminder ? "우연한 식탁이 곧 열려요" : "오늘 우연한 식탁은 어떠셨나요?",
                user -> isReminder
                        ? "하루 앞으로 다가왔어요. 테이블 상세에서 시간과 식당을 확인해 주세요."
                        : "함께한 테이블에 대한 피드백을 남겨 주세요. 다음 매칭에 반영돼요.",
                isReminder ? NotificationLink.DINING_TABLE : NotificationLink.DINING_FEEDBACK, event.getTableId(),
                new Target(event.getSessionId(), event.getTableId(), null));
    }

    /**
     * 새 회차 모집 → 최근 6개월 안에 참석(ATTENDED)한 회원에게 1건(linkId = 모임 종류 ID).
     * ponytail: 지역·요일 선호 추천 대신 "이전에 참석한 회차와 같은 장소면 문구에 장소를 넣어 먼저 권한다" 수준. 발송 상한이나 선호 점수가 필요해지면 대상 선정을 따로 만든다.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onSessionsCreated(DiningSessionsCreatedEvent event) {
        Map<UUID, Set<UUID>> attendees = diningAttendanceService.findRecentAttendeeLocations(
                LocalDate.now().minusMonths(NEXT_SESSIONS_LOOKBACK_MONTHS));
        notifyUsers(attendees.keySet(), NotificationType.DINING_NEXT_SESSIONS,
                "우연한 식탁 새 회차가 열렸어요",
                user -> attendees.get(user.getId()).contains(event.getLocationId())
                        ? "지난번 함께한 " + event.getLocationName() + "에서 " + event.getGatheringTitle() + " 새 회차가 열렸어요. 일정을 확인해 보세요."
                        : event.getGatheringTitle() + " 새 회차가 열렸어요. 일정을 확인해 보세요.",
                NotificationLink.DINING_HISTORY, event.getGatheringId(), new Target(null, null, null));
    }

    // 예외함 연결 대상
    private record Target(UUID sessionId, UUID tableId, UUID applicationId) {
    }

    // 탈퇴하지 않은 회원마다 따로 적재한다. 같은 회원이 여러 번 들어와도 1건.
    private void notifyUsers(Collection<UUID> userIds, NotificationType type, String title, Function<User, String> content,
                             NotificationLink link, UUID linkId, Target target) {
        if (userIds.isEmpty()) {
            return;
        }
        userService.findUsersByIds(Set.copyOf(userIds)).values().stream()
                .filter(user -> !user.isWithdrawn())
                .forEach(user -> createWithRetry(user, type, title, content.apply(user), link, linkId, target));
    }

    private void createWithRetry(User user, NotificationType type, String title, String content, NotificationLink link,
                                 UUID linkId, Target target) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                inNewTransaction(() -> userNotificationService.create(user, type, title, content, link, linkId));
                return;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("알림 적재 실패 {}/{}: type={}, userId={}", attempt, MAX_ATTEMPTS, type, user.getId(), e);
            }
        }
        String reason = "[" + type + "] 알림 " + MAX_ATTEMPTS + "회 실패(userId=" + user.getId() + "): " + lastError.getMessage();
        try {
            inNewTransaction(() -> exceptionCaseService.open(ExceptionCaseType.NOTIFICATION,
                    target.sessionId(), target.tableId(), target.applicationId(), reason));
        } catch (RuntimeException e) {
            log.error("알림 실패 예외함 기록 실패: {}", reason, e);
        }
    }

    // 커밋 뒤(AFTER_COMMIT) 호출되므로 쓰기는 반드시 새 트랜잭션에서 한다.
    private void inNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }
}
