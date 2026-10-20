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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 문의방 고객 메시지 → 그록봇 대기 문의 1건. 같은 방의 PENDING은 디바운스로 마지막 메시지 하나에 묶인다.
 * 에이전트가 GET /api/bot/pending으로 가져가면 LEASED(leased_until까지), 만료되면 PENDING으로 돌아가고
 * 3회 lease가 모두 만료되면 FAILED. (KAN-406)
 */
@Entity
@Table(name = "bot_dispatch")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotDispatch {

    public static final String LEASE_EXHAUSTED_REASON = "응답 없음(조회 3회 만료)";
    static final int MAX_LEASES = 3;

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

    @Column(name = "send_after", nullable = false)
    private LocalDateTime sendAfter;

    // LEASED일 때 답변을 받는 기한.
    @Column(name = "leased_until")
    private LocalDateTime leasedUntil;

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

    /** 디바운스: 대기 중인 건을 새 메시지로 바꾸고 조회 가능 시각(send_after)을 미룬다. */
    public void debounce(UUID messageId, LocalDateTime sendAfter) {
        this.messageId = messageId;
        this.sendAfter = sendAfter;
    }

    /** 에이전트가 가져감. attempts는 lease 횟수. */
    public void lease(LocalDateTime leasedUntil) {
        this.status = BotDispatchStatus.LEASED;
        this.leasedUntil = leasedUntil;
        this.attempts++;
    }

    /** 만료된 lease를 다시 대기로. send_after를 지금으로 당겨 신호·미처리 타임아웃이 이 시각부터 다시 센다. */
    public void requeue(LocalDateTime now) {
        this.status = BotDispatchStatus.PENDING;
        this.leasedUntil = null;
        this.sendAfter = now;
    }

    public boolean isLeaseExhausted() {
        return attempts >= MAX_LEASES;
    }

    public boolean isLeaseExpired(LocalDateTime now) {
        return leasedUntil == null || !leasedUntil.isAfter(now);
    }

    public void markFailed(String reason) {
        this.status = BotDispatchStatus.FAILED;
        this.handoffReason = reason;
    }

    public void markReplied() {
        this.status = BotDispatchStatus.REPLIED;
    }

    public void markHandedOff(String reason) {
        this.status = BotDispatchStatus.HANDED_OFF;
        this.handoffReason = reason;
    }

    public boolean isAnswered() {
        return status == BotDispatchStatus.REPLIED || status == BotDispatchStatus.HANDED_OFF;
    }
}
