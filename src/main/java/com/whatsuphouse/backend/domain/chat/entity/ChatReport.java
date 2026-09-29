package com.whatsuphouse.backend.domain.chat.entity;

import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatReport(UUID messageId, UUID reporterId, String reason) {
        this.messageId = messageId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.status = ChatReportStatus.OPEN;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatReport of(UUID messageId, UUID reporterId, String reason) {
        return new ChatReport(messageId, reporterId, reason);
    }

    public void changeStatus(ChatReportStatus status) {
        this.status = status;
    }
}
