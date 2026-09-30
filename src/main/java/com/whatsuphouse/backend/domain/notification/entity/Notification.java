package com.whatsuphouse.backend.domain.notification.entity;

import com.whatsuphouse.backend.domain.notification.enums.NotificationLink;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NotificationLink link;

    // link가 가리키는 대상 ID(DINING_TABLE이면 테이블 ID). 대상이 필요 없는 링크는 NULL. (KAN-346)
    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public Notification(User user, NotificationType type, String title, String content, NotificationLink link,
                        UUID linkId) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.link = link;
        this.linkId = linkId;
        this.read = false;
    }

    /** 읽음 처리 (멱등). */
    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}
