package com.whatsuphouse.backend.domain.payment.entity;

import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import com.whatsuphouse.backend.domain.payment.enums.DepositSource;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 받은 입금 알림 원문 1건과 대사 결과. 같은 원문(text_hash)은 한 번만 저장된다. (설계 2, KAN-403) */
@Entity
@Table(name = "deposit_notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "text_hash", nullable = false, unique = true, length = 64)
    private String textHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositSource source;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "parsed_name", length = 100)
    private String parsedName;

    @Column(name = "parsed_amount")
    private Integer parsedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DepositMatchResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private PaymentTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "exception_case_id")
    private UUID exceptionCaseId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수신 기록. 대사 전이라 결과는 일단 IGNORED이고 처리 단계에서 바뀐다. */
    public static DepositNotification receive(String rawText, String textHash, DepositSource source, LocalDateTime receivedAt) {
        DepositNotification notification = new DepositNotification();
        notification.rawText = rawText;
        notification.textHash = textHash;
        notification.source = source;
        notification.receivedAt = receivedAt;
        notification.result = DepositMatchResult.IGNORED;
        return notification;
    }

    public void parsed(String name, int amount) {
        this.parsedName = name;
        this.parsedAmount = amount;
    }

    /** 결제 대기 건에 연결(자동 대사 또는 운영자 수동 연결). 예외 건 ID는 이력으로 남긴다. */
    public void match(PendingPayment target) {
        this.result = DepositMatchResult.MATCHED;
        this.targetType = target.type();
        this.targetId = target.targetId();
        this.applicationId = target.applicationId();
    }

    public void fail(DepositMatchResult result, UUID exceptionCaseId) {
        this.result = result;
        this.exceptionCaseId = exceptionCaseId;
    }
}
