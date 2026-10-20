package com.whatsuphouse.backend.domain.notification;

import com.whatsuphouse.backend.domain.chat.service.ChatPushService;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.AdminDepositNotifiedEvent;
import com.whatsuphouse.backend.domain.notification.service.UserNotificationService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/** 입금 대사 결과 관리자 알림: 활성 관리자마다 1건, 원문·계좌 없음, 웹 푸시는 같은 관리자에게. (KAN-416) */
@ExtendWith(MockitoExtension.class)
class AdminDepositNotificationListenerTest {

    @Mock private UserNotificationService userNotificationService;
    @Mock private UserService userService;
    @Mock private ChatPushService chatPushService;
    // 실제 트랜잭션 대신 콜백만 실행된다.
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks
    private AdminDepositNotificationListener listener;

    private final UUID depositNotificationId = UUID.randomUUID();
    private User admin1;
    private User admin2;

    @BeforeEach
    void setUp() {
        admin1 = user("a1@example.com");
        admin2 = user("a2@example.com");
        User withdrawn = user("gone@example.com");
        withdrawn.withdraw();
        List<UUID> ids = List.of(admin1.getId(), admin2.getId(), withdrawn.getId());
        lenient().when(userService.listActiveAdminIds()).thenReturn(ids);
        lenient().when(userService.findUsersByIds(ids)).thenReturn(Map.of(
                admin1.getId(), admin1, admin2.getId(), admin2, withdrawn.getId(), withdrawn));
    }

    private static User user(String email) {
        User user = User.builder().email(email).password("p").name("관리자").build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    @DisplayName("자동 매칭: 활성 관리자마다 ADMIN_DEPOSIT_MATCHED 1건(입금 관리 링크), 탈퇴 관리자는 제외")
    void onDepositNotified_matched_notifiesEachActiveAdmin() {
        // when
        listener.onDepositNotified(new AdminDepositNotifiedEvent(depositNotificationId, true, false, "한지형", 9_900));

        // then
        ArgumentCaptor<User> recipients = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contents = ArgumentCaptor.forClass(String.class);
        then(userNotificationService).should(times(2)).create(recipients.capture(), eq(NotificationType.ADMIN_DEPOSIT_MATCHED),
                titles.capture(), contents.capture(), eq(NotificationLink.ADMIN_PAYMENTS), eq(depositNotificationId));
        assertThat(recipients.getAllValues()).containsExactlyInAnyOrder(admin1, admin2);
        assertThat(titles.getValue()).isEqualTo("입금 확인: 한지형 9,900원 — 자동 처리됨");
        assertThat(titles.getValue() + contents.getValue()).doesNotContain("1002", "잔액", "계좌");
        ArgumentCaptor<Collection<UUID>> pushed = ArgumentCaptor.forClass(Collection.class);
        then(chatPushService).should().pushToUsers(pushed.capture(), eq(new ChatPushService.Payload(
                titles.getValue(), contents.getValue(), "/admin/payments")));
        assertThat(pushed.getValue()).containsExactlyInAnyOrder(admin1.getId(), admin2.getId());
    }

    @Test
    @DisplayName("예외함: ADMIN_DEPOSIT_EXCEPTION, 제목은 '입금 확인 필요: … — 예외함'")
    void onDepositNotified_exception_notifiesException() {
        // when
        listener.onDepositNotified(new AdminDepositNotifiedEvent(depositNotificationId, false, false, "한지형", 59_900));

        // then
        then(userNotificationService).should(times(2)).create(any(), eq(NotificationType.ADMIN_DEPOSIT_EXCEPTION),
                eq("입금 확인 필요: 한지형 59,900원 — 예외함"), any(), eq(NotificationLink.ADMIN_PAYMENTS),
                eq(depositNotificationId));
    }

    @Test
    @DisplayName("한 관리자 적재가 실패해도 다른 관리자 알림과 푸시는 계속된다")
    void onDepositNotified_oneAdminFails_othersContinue() {
        // given
        willThrow(new IllegalStateException("db")).given(userNotificationService).create(eq(admin1), any(), any(), any(),
                any(), any());

        // when
        listener.onDepositNotified(new AdminDepositNotifiedEvent(depositNotificationId, true, true, "한지형", 9_900));

        // then
        then(userNotificationService).should().create(eq(admin2), eq(NotificationType.ADMIN_DEPOSIT_MATCHED),
                eq("입금 확인: 한지형 9,900원 — 수동 연결됨"), any(), any(), any());
        then(chatPushService).should().pushToUsers(any(), any());
    }

    @Test
    @DisplayName("파싱 실패(이름·금액 없음)는 '읽지 못한 입금 알림'으로 표시한다")
    void title_parseFailed_placeholder() {
        assertThat(AdminDepositNotificationListener.title(
                new AdminDepositNotifiedEvent(depositNotificationId, false, false, null, null)))
                .isEqualTo("입금 확인 필요: 읽지 못한 입금 알림 — 예외함");
        then(userNotificationService).should(never()).create(any(), any(), any(), any(), any(), any());
    }
}
