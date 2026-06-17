package com.whatsuphouse.backend.domain.notification.entity;

import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private Notification build() {
        User user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        return Notification.builder()
                .user(user)
                .type(NotificationType.MILEAGE_EARNED)
                .title("마일리지가 적립되었어요")
                .content("500M가 적립되었어요.")
                .link(NotificationLink.MILEAGE)
                .build();
    }

    @Test
    @DisplayName("생성 직후엔 읽지 않은 상태다")
    void newNotification_isUnread() {
        Notification notification = build();
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("읽음 처리하면 read=true, readAt이 설정된다")
    void markAsRead_setsRead() {
        Notification notification = build();
        notification.markAsRead();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("읽음 처리는 멱등 — 이미 읽었으면 readAt을 유지한다")
    void markAsRead_idempotent() {
        Notification notification = build();
        notification.markAsRead();
        LocalDateTime firstReadAt = notification.getReadAt();

        notification.markAsRead();

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }
}
