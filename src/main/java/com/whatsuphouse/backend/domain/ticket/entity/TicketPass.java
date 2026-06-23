package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_passes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketPass extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 이용권 소유자. 회원/비회원 모두 participant로 연결한다. (KAN-276)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketProduct product;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "remaining_count", nullable = false)
    private int remainingCount;

    @Column(name = "purchase_amount", nullable = false)
    private int purchaseAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPassStatus status;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    @Column(name = "payment_confirmed_at")
    private LocalDateTime paymentConfirmedAt;

    @Builder
    public TicketPass(Participant participant, TicketProduct product) {
        this.participant = participant;
        this.product = product;
        this.totalCount = product.getSessionCount();
        this.remainingCount = 0;            // 입금 확인 전까지 사용 불가
        this.purchaseAmount = product.getPrice();
        this.status = TicketPassStatus.PENDING;
        this.paymentDeadline = LocalDateTime.now().plusDays(3);
    }

    /** 소유자가 회원이면 그 User를, 비회원이면 null을 반환한다. (KAN-276) */
    public User getUser() {
        return participant != null ? participant.getUser() : null;
    }

    /** 관리자 입금 확인 시 활성화하고 잔여를 충전한다. PENDING이 아니면 예외. */
    public void activate() {
        if (this.status != TicketPassStatus.PENDING) {
            throw new CustomException(ErrorCode.TICKET_ALREADY_PROCESSED);
        }
        this.status = TicketPassStatus.ACTIVE;
        this.remainingCount = this.totalCount;
        this.activatedAt = LocalDateTime.now();
        this.paymentConfirmedAt = this.activatedAt;
    }

    /** 우연한 식탁 신청 시 1회 차감한다. 사용 가능 상태가 아니면 예외. */
    public void deductOne() {
        if (this.status != TicketPassStatus.ACTIVE || this.remainingCount <= 0) {
            throw new CustomException(ErrorCode.NO_AVAILABLE_TICKET);
        }
        this.remainingCount -= 1;
        if (this.remainingCount == 0) {
            this.status = TicketPassStatus.USED_UP;
        }
    }

    /** 신청 취소 시 1회 환불한다. 이미 가득 차 있으면 무시. */
    public void refundOne() {
        if (this.remainingCount >= this.totalCount) {
            return;
        }
        this.remainingCount += 1;
        if (this.status == TicketPassStatus.USED_UP) {
            this.status = TicketPassStatus.ACTIVE;
        }
    }

    public boolean isUsable() {
        return this.status == TicketPassStatus.ACTIVE && this.remainingCount > 0;
    }

    public void adjustRemaining(int quantity) {
        int adjusted = this.remainingCount + quantity;
        if (quantity == 0 || adjusted < 0) {
            throw new CustomException(ErrorCode.INVALID_TICKET_ADJUSTMENT);
        }
        this.remainingCount = adjusted;
        this.status = adjusted == 0 ? TicketPassStatus.USED_UP : TicketPassStatus.ACTIVE;
    }
}
