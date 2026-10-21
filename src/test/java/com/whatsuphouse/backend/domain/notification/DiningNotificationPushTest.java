package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.chat.service.ChatPushService;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.NoticeLogService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningResolutionEvent;
import com.whatsuphouse.backend.domain.notification.event.DiningTableConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.DiningTableLifecycleEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/** 우연한 식탁 확정·리마인드·매칭 실패 알림의 웹 푸시: 인앱 알림과 같은 제목·본문을 같은 회원에게. (KAN-438) */
@ExtendWith(MockitoExtension.class)
class DiningNotificationPushTest {

    @Mock private UserNotificationService userNotificationService;
    @Mock private UserService userService;
    @Mock private DiningAttendanceService diningAttendanceService;
    @Mock private ExceptionCaseService exceptionCaseService;
    @Mock private ChatPushService chatPushService;
    @Mock private NoticeLogService noticeLogService;
    // 실제 트랜잭션 대신 콜백만 실행된다.
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private DiningConfirmNotificationListener confirmListener;
    @InjectMocks private DiningLifecycleNotificationListener lifecycleListener;
    @InjectMocks private DiningResolutionNotificationListener resolutionListener;

    private final UUID tableId = UUID.randomUUID();

    @Test
    @DisplayName("테이블 확정: 멤버에게 인앱 알림과 같은 제목·본문, 테이블 상세 경로로 푸시를 넘기고 NOTIFICATION/PUSH 이력을 남긴다. 넘기다 실패해도 예외를 던지지 않고 실패로 남긴다")
    void pushDiningTableConfirmed_sendsSameTitleAndBody() {
        // given
        List<UUID> memberIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        given(chatPushService.isEnabled()).willReturn(true);

        // when
        confirmListener.pushDiningTableConfirmed(new DiningTableConfirmedEvent(tableId, UUID.randomUUID(), memberIds));

        // then
        then(chatPushService).should().pushToUsers(memberIds, new ChatPushService.Payload(
                DiningConfirmNotificationListener.TITLE, DiningConfirmNotificationListener.CONTENT, "/dining/tables/" + tableId));
        then(noticeLogService).should().recordAfterCommit(tableId, NoticeKind.NOTIFICATION, NoticeChannel.PUSH, null);

        // when & then: 실행기 포화 등으로 넘기기가 실패해도 확정 흐름을 막지 않는다
        TaskRejectedException rejected = new TaskRejectedException("full");
        willThrow(rejected).given(chatPushService).pushToUsers(any(), any());
        assertThatCode(() -> confirmListener.pushDiningTableConfirmed(
                new DiningTableConfirmedEvent(tableId, UUID.randomUUID(), memberIds))).doesNotThrowAnyException();
        then(noticeLogService).should().recordAfterCommit(tableId, NoticeKind.NOTIFICATION, NoticeChannel.PUSH, rejected);
    }

    @Test
    @DisplayName("웹 푸시가 꺼져 있으면(VAPID 미설정) 보내지도, 발송 이력을 남기지도 않는다")
    void pushDiningTableConfirmed_disabled_skips() {
        // given
        given(chatPushService.isEnabled()).willReturn(false);

        // when
        confirmListener.pushDiningTableConfirmed(new DiningTableConfirmedEvent(tableId, UUID.randomUUID(), List.of(UUID.randomUUID())));

        // then
        then(chatPushService).should(never()).pushToUsers(any(), any());
        then(noticeLogService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("리마인드: 인앱 알림을 받은 회원(탈퇴 제외)에게 같은 제목·본문으로 푸시하고 REMINDER/PUSH 이력을 남긴다, 피드백 요청은 푸시하지 않는다")
    void onTableLifecycle_reminderPushesToRecipients() {
        // given
        given(chatPushService.isEnabled()).willReturn(true);
        User member = user();
        User withdrawn = user();
        withdrawn.withdraw();
        given(userService.findUsersByIds(Set.of(member.getId(), withdrawn.getId())))
                .willReturn(Map.of(member.getId(), member, withdrawn.getId(), withdrawn));

        // when
        lifecycleListener.onTableLifecycle(new DiningTableLifecycleEvent(NotificationType.DINING_REMINDER, tableId,
                UUID.randomUUID(), List.of(member.getId(), withdrawn.getId())));

        // then
        then(userNotificationService).should().create(member, NotificationType.DINING_REMINDER, "우연한 식탁이 곧 열려요",
                "하루 앞으로 다가왔어요. 테이블 상세에서 시간과 식당을 확인해 주세요.", NotificationLink.DINING_TABLE, tableId);
        then(chatPushService).should().pushToUsers(List.of(member.getId()), new ChatPushService.Payload(
                "우연한 식탁이 곧 열려요", "하루 앞으로 다가왔어요. 테이블 상세에서 시간과 식당을 확인해 주세요.",
                "/dining/tables/" + tableId));

        // when: 피드백 요청
        lifecycleListener.onTableLifecycle(new DiningTableLifecycleEvent(NotificationType.DINING_FEEDBACK_REQUEST, tableId,
                UUID.randomUUID(), List.of(member.getId(), withdrawn.getId())));

        // then: 푸시·이력은 리마인드 1번뿐
        then(chatPushService).should().pushToUsers(any(), any());
        then(noticeLogService).should().recordAfterCommit(tableId, NoticeKind.REMINDER, NoticeChannel.PUSH, null);
        then(noticeLogService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("매칭 실패(해결 선택 요청): 신청자에게 인앱 알림과 같은 제목·본문으로 해결 선택 경로 푸시, 다른 해결 알림은 푸시하지 않는다")
    void pushAlternativeOffered_onlyForAlternativeOffered() {
        // given
        User member = user();
        Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();
        Application application = Application.builder().bookingNumber("WH-1").gathering(gathering).user(member)
                .name("회원").phone("01012345678").build();
        application.changeMatchStatus(MatchStatus.NO_MATCH);

        // when
        resolutionListener.pushAlternativeOffered(new DiningResolutionEvent(application, NotificationType.DINING_ALTERNATIVE_OFFERED));
        resolutionListener.pushAlternativeOffered(new DiningResolutionEvent(application, NotificationType.DINING_TRANSFERRED));

        // then
        then(chatPushService).should().pushToUsers(eq(List.of(member.getId())), eq(new ChatPushService.Payload(
                "테이블 배정이 어려워요",
                "우연한 식탁 희망 회차에 맞는 테이블을 찾지 못했어요. 이용권 보관 또는 환불을 선택해 주세요.",
                "/mypage?tab=applications#dining-resolution")));
        then(chatPushService).shouldHaveNoMoreInteractions();
        then(userNotificationService).should(never()).create(any(), any(), any(), any(), any());
    }

    private static User user() {
        User user = User.builder().email(UUID.randomUUID() + "@example.com").password("p").name("회원").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
