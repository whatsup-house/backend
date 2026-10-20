package com.whatsuphouse.backend.domain.payment.entity;

import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.user.entity.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * 운영자가 직접 이체해야 하는 송금 1건(환불·보증금 반환). 계좌는 이 건에만 저장하고 송금 30일 후 끝 4자리만 남긴다.
 * 대상 신청·이용권·보증금은 ID만 들고 있다(후속 처리는 PayoutSentEvent 리스너). (설계 3.3·4.3, KAN-404)
 */
@Entity
@Table(name = "payout_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutType type;

    @Column(nullable = false)
    private int amount;

    // 보증금 반환은 신청 때 받은 계좌를 그대로 쓰며, 계좌 없이 만든 옛 보증금이면 비어 있을 수 있다.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "bank", column = @Column(name = "bank", length = 50)),
            @AttributeOverride(name = "accountNumber", column = @Column(name = "account_number", length = 50)),
            @AttributeOverride(name = "holder", column = @Column(name = "holder", length = 50))
    })
    private RefundAccount account;

    @Column(name = "account_masked", nullable = false)
    private boolean accountMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "ticket_pass_id")
    private UUID ticketPassId;

    @Column(name = "deposit_id")
    private UUID depositId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // 송금 완료 처리한 운영자. 데모 자동 송금이면 null.
    @Column(name = "sent_by")
    private UUID sentBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PayoutRequest request(User user, PayoutType type, int amount, RefundAccount account,
                                        UUID applicationId, UUID ticketPassId, UUID depositId) {
        PayoutRequest payout = new PayoutRequest();
        payout.user = user;
        payout.type = type;
        payout.amount = amount;
        payout.account = account;
        payout.status = PayoutStatus.REQUESTED;
        payout.applicationId = applicationId;
        payout.ticketPassId = ticketPassId;
        payout.depositId = depositId;
        return payout;
    }

    /** 송금 완료. REQUESTED일 때만 바꾸고 true(이미 SENT면 false, 멱등). */
    public boolean markSent(UUID adminId, LocalDateTime now) {
        if (status != PayoutStatus.REQUESTED) {
            return false;
        }
        status = PayoutStatus.SENT;
        sentAt = now;
        sentBy = adminId;
        return true;
    }

    public void maskAccount() {
        if (account != null) {
            account = account.masked();
        }
        accountMasked = true;
    }
}
