package com.whatsuphouse.backend.domain.notification.service;

import com.whatsuphouse.backend.domain.notification.dto.response.NotificationResponse;
import com.whatsuphouse.backend.domain.notification.entity.Notification;
import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.repository.NotificationRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserNotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 인앱 알림을 적재한다. 대상 user가 없으면(비회원 등) 무시한다.
     * 호출자의 트랜잭션 내에서 실행되어, 발행 트랜잭션이 롤백되면 알림도 롤백된다.
     */
    public void create(User user, NotificationType type, String title, String content, NotificationLink link) {
        if (user == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .content(content)
                .link(link)
                .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMyNotifications(UUID userId) {
        return notificationRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUser_IdAndReadFalseAndDeletedAtIsNull(userId);
    }

    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    public void delete(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.delete();
    }
}
