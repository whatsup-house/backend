package com.whatsuphouse.backend.domain.bot.entity;

import com.whatsuphouse.backend.domain.bot.enums.BotDispatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 문의방 고객 메시지 → 그록봇 웹훅 발송 1건. 같은 방의 PENDING은 디바운스로 마지막 메시지 하나에 묶인다.
 * 실패 시 5초 → 30초 → 2분 뒤 재시도, 최초+3회 실패면 FAILED. last_error엔 상태 코드·예외 클래스만. (KAN-406)
 */
@Entity
@Table(name = "bot_dispatch")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotDispatch {

    public static final String FAILED_REASON = "웹훅 발송 실패";
    private static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    // 문의방 ID
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BotDispatchStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "send_after", nullable = false)
    private LocalDateTime sendAfter;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // 관리자만 본다.
    @Column(name = "handoff_reason", columnDefinition = "TEXT")
    private String handoffReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private BotDispatch(UUID conversationId, UUID messageId, UUID customerId, BotDispatchStatus status, LocalDateTime sendAfter) {
        this.eventId = UUID.randomUUID();
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.customerId = customerId;
        this.status = status;
        this.sendAfter = sendAfter;
    }

    public static BotDispatch pending(UUID conversationId, UUID messageId, UUID customerId, LocalDateTime sendAfter) {
        return new BotDispatch(conversationId, messageId, customerId, BotDispatchStatus.PENDING, sendAfter);
    }

    /** 한도 초과 등으로 보내지 않고 바로 이관한 건. 한도 집계에 들어가도록 행은 남긴다. */
    public static BotDispatch handedOffWithoutSending(UUID conversationId, UUID messageId, UUID customerId,
                                                      LocalDateTime now, String reason) {
        BotDispatch dispatch = new BotDispatch(conversationId, messageId, customerId, BotDispatchStatus.HANDED_OFF, now);
        dispatch.handoffReason = reason;
        return dispatch;
    }

    /** 디바운스: 대기 중인 건을 새 메시지로 바꾸고 발송 시각을 미룬다. */
    public void debounce(UUID messageId, LocalDateTime sendAfter) {
        this.messageId = messageId;
        this.sendAfter = sendAfter;
    }

    public void markSent(LocalDateTime now) {
        this.status = BotDispatchStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
    }

    /** 발송 실패 1회 기록. 남은 재시도가 없으면 FAILED. error는 상태 코드·예외 클래스명만. */
    public void recordFailure(LocalDateTime now, String error) {
        this.attempts++;
        this.lastError = error;
        if (attempts > BACKOFF.size()) {
            this.status = BotDispatchStatus.FAILED;
            this.nextAttemptAt = null;
            this.handoffReason = FAILED_REASON;
            return;
        }
        this.nextAttemptAt = now.plus(BACKOFF.get(attempts - 1));
    }

    public void markReplied() {
        this.status = BotDispatchStatus.REPLIED;
    }

    public void markHandedOff(String reason) {
        this.status = BotDispatchStatus.HANDED_OFF;
        this.handoffReason = reason;
    }

    public boolean isFailed() {
        return status == BotDispatchStatus.FAILED;
    }

    public boolean isAnswered() {
        return status == BotDispatchStatus.REPLIED || status == BotDispatchStatus.HANDED_OFF;
    }
}
