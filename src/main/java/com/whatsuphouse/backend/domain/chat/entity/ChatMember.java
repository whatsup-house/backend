package com.whatsuphouse.backend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "chat_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_members_room_user", columnNames = {"room_id", "user_id"}),
        indexes = @Index(name = "idx_chat_members_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    // NULL = 참여 중. 나가기·내보내기 시 기록, 재초대 시 NULL로 되돌린다.
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    // 문의방 목록 숨김(사용자만). 문의방을 다시 열거나 새 메시지가 오면 해제된다.
    @Column(nullable = false)
    private boolean hidden = false;

    private ChatMember(UUID roomId, UUID userId, UUID lastReadMessageId) {
        this.roomId = roomId;
        this.userId = userId;
        this.joinedAt = LocalDateTime.now();
        this.lastReadMessageId = lastReadMessageId;
    }

    /** lastReadMessageId: 합류 시점의 마지막 메시지. 과거 기록은 보이되 안읽은 수에는 잡히지 않는다. */
    public static ChatMember join(UUID roomId, UUID userId, UUID lastReadMessageId) {
        return new ChatMember(roomId, userId, lastReadMessageId);
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public void leave() {
        this.leftAt = LocalDateTime.now();
    }

    /** 재초대. 메시지 조회는 joined_at 필터가 없으므로 나가 있던 동안의 기록까지 전체가 보인다. */
    public void rejoin(UUID lastReadMessageId) {
        this.leftAt = null;
        this.hidden = false;
        this.joinedAt = LocalDateTime.now();
        this.lastReadMessageId = lastReadMessageId;
    }

    public void hide() {
        this.hidden = true;
    }

    public void unhide() {
        this.hidden = false;
    }

    public void markRead(UUID messageId) {
        this.lastReadMessageId = messageId;
    }
}
