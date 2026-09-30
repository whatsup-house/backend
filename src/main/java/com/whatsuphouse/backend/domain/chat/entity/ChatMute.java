package com.whatsuphouse.backend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** 채팅 금지. 계정 정지와 별개이며 행이 있으면 뮤트 상태다. */
@Entity
@Table(name = "chat_mutes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMute {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "muted_by", nullable = false)
    private UUID mutedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatMute(UUID userId, UUID mutedBy, String reason) {
        this.userId = userId;
        this.mutedBy = mutedBy;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatMute of(UUID userId, UUID mutedBy, String reason) {
        return new ChatMute(userId, mutedBy, reason);
    }

    public void update(UUID mutedBy, String reason) {
        this.mutedBy = mutedBy;
        this.reason = reason;
    }
}
