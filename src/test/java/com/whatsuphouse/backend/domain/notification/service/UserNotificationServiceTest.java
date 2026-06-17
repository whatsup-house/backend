package com.whatsuphouse.backend.domain.notification.service;

import com.whatsuphouse.backend.domain.notification.dto.response.NotificationResponse;
import com.whatsuphouse.backend.domain.notification.entity.Notification;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.repository.NotificationRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserNotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private UserNotificationService userNotificationService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .email("t@example.com").password("p").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("nick").phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    private Notification buildNotification() {
        return Notification.builder()
                .user(user)
                .type(NotificationType.PARTICIPATION_CONFIRMED)
                .title("참가가 확정되었어요")
                .content("재즈 게더링 참가가 확정되었습니다.")
                .link(NotificationLink.APPLICATIONS)
                .build();
    }

    @Test
    @DisplayName("알림을 적재한다")
    void create_savesNotification() {
        userNotificationService.create(user, NotificationType.MILEAGE_EARNED,
                "마일리지가 적립되었어요", "500M가 적립되었어요.", NotificationLink.MILEAGE);

        then(notificationRepository).should().save(any(Notification.class));
    }

    @Test
    @DisplayName("대상 회원이 없으면(null) 알림을 적재하지 않는다")
    void create_nullUser_skips() {
        userNotificationService.create(null, NotificationType.MILEAGE_EARNED,
                "t", "c", NotificationLink.MILEAGE);

        then(notificationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("미확인 알림 개수를 조회한다")
    void getUnreadCount_delegates() {
        given(notificationRepository.countByUser_IdAndReadFalseAndDeletedAtIsNull(userId)).willReturn(3L);

        assertThat(userNotificationService.getUnreadCount(userId)).isEqualTo(3L);
    }

    @Test
    @DisplayName("내 알림 목록을 응답으로 변환한다")
    void listMyNotifications_maps() {
        given(notificationRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId))
                .willReturn(List.of(buildNotification()));

        List<NotificationResponse> result = userNotificationService.listMyNotifications(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(NotificationType.PARTICIPATION_CONFIRMED);
        assertThat(result.get(0).getLink()).isEqualTo(NotificationLink.APPLICATIONS);
    }

    @Test
    @DisplayName("읽음 처리하면 해당 알림이 읽음 상태가 된다")
    void markAsRead_marks() {
        UUID id = UUID.randomUUID();
        Notification notification = buildNotification();
        given(notificationRepository.findByIdAndUser_IdAndDeletedAtIsNull(id, userId))
                .willReturn(Optional.of(notification));

        userNotificationService.markAsRead(id, userId);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 예외")
    void markAsRead_notFound_throws() {
        UUID id = UUID.randomUUID();
        given(notificationRepository.findByIdAndUser_IdAndDeletedAtIsNull(id, userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> userNotificationService.markAsRead(id, userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제하면 소프트 삭제된다")
    void delete_softDeletes() {
        UUID id = UUID.randomUUID();
        Notification notification = buildNotification();
        given(notificationRepository.findByIdAndUser_IdAndDeletedAtIsNull(id, userId))
                .willReturn(Optional.of(notification));

        userNotificationService.delete(id, userId);

        assertThat(notification.getDeletedAt()).isNotNull();
    }
}
