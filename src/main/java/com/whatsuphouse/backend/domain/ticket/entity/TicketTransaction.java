package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_pass_id", nullable = false)
    private TicketPass ticketPass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TicketTransactionType transactionType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 255)
    private String reason;

    // 차감(USE) 레코드의 상태. 다른 거래 유형은 NULL. (KAN-342)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketDeductionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static TicketTransaction of(TicketPass pass, Application application,
                                       TicketTransactionType type, int quantity, String reason) {
        TicketTransaction transaction = new TicketTransaction();
        transaction.ticketPass = pass;
        transaction.application = application;
        transaction.transactionType = type;
        transaction.quantity = quantity;
        transaction.balanceAfter = pass.getRemainingCount();
        transaction.reason = reason;
        transaction.status = type == TicketTransactionType.USE ? TicketDeductionStatus.DEDUCTED : null;
        return transaction;
    }

    /** 취소로 차감을 되돌렸다. 잔여 복구는 같은 흐름의 REFUND 거래가 기록한다. */
    public void restore() {
        this.status = TicketDeductionStatus.RESTORED;
    }

    /** 아직 복원·환불되지 않은 차감. V8 백필 전 USE 행은 status가 비어 있어 차감으로 본다. */
    public boolean isDeducted() {
        return transactionType == TicketTransactionType.USE
                && (status == null || status == TicketDeductionStatus.DEDUCTED);
    }

    /** 환불 단계를 한 칸 진행한다. DEDUCTED → REFUND_REQUESTED → REFUND_PROCESSING → REFUNDED 순서만 허용. (KAN-347) */
    public void advanceRefund(TicketDeductionStatus next) {
        boolean allowed = switch (next) {
            case REFUND_REQUESTED -> isDeducted();
            case REFUND_PROCESSING -> status == TicketDeductionStatus.REFUND_REQUESTED;
            case REFUNDED -> status == TicketDeductionStatus.REFUND_PROCESSING;
            default -> false;
        };
        if (!allowed) {
            throw new CustomException(ErrorCode.INVALID_REFUND_TRANSITION);
        }
        this.status = next;
    }

    public void failRefund() {
        this.status = TicketDeductionStatus.REFUND_FAILED;
    }
}
