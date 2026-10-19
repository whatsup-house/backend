package com.whatsuphouse.backend.domain.ticket.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    // 이용권 소유자. 우연한 식탁 회원 전용 전환으로 항상 회원(User)이다.
    // DB 컬럼은 레거시 비회원 이용권 정리 전까지 nullable 유지 (V4 마이그레이션 참고).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 특정 신청의 결제 대기 상태에서 생성된 이용권 구매 요청이면 해당 신청과 직접 연결한다. (KAN-289)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private TicketProductOption productOption;

    @Column(name = "product_name", length = 100)
    private String productName;

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

    // 멤버십(PERIOD) 만료일(포함). 횟수권은 null. (KAN-401)
    @Column(name = "valid_until")
    private LocalDate validUntil;

    // 매칭 실패로 연장된 누적 일수.
    @Column(name = "extended_days", nullable = false)
    private int extendedDays;

    public TicketPass(User user, Application application, TicketProductOption productOption) {
        this.user = user;
        this.application = application;
        this.productOption = productOption;
        this.productName = productOption.getName();
        this.totalCount = productOption.getSessionCount();
        this.remainingCount = 0;            // 입금 확인 전까지 사용 불가
        this.purchaseAmount = productOption.getPrice();
        this.status = TicketPassStatus.PENDING;
        this.paymentDeadline = LocalDateTime.now().plusDays(3);
    }

    public String getProductLabel() {
        return productName != null && !productName.isBlank() ? productName : "이용권";
    }

    // ─────────────────────────────────────────────────────────────
    // [잔액-원장 불변식] 아래 잔여 수 변경 메서드(activate/deductOne/refundOne/adjustRemaining)는
    // 반드시 TicketTransaction 기록을 함께 남기는 서비스 메서드에서만 호출한다.
    // 원장 없이 잔액만 바꾸는 경로가 생기면 잔여 수와 거래내역이 어긋나 복구가 불가능해진다.
    // 검증: sum(transaction.amount) == totalCount - remainingCount (TicketServiceTest 참조)
    // ─────────────────────────────────────────────────────────────

    /** 관리자 입금 확인 시 활성화하고 잔여를 충전한다. PENDING이 아니면 예외. */
    public void activate() {
        activate(LocalDateTime.now());
    }

    /** 멤버십은 활성화 날짜 + N개월을 만료일로 잡는다(잔여는 0 그대로). */
    public void activate(LocalDateTime now) {
        if (this.status != TicketPassStatus.PENDING) {
            throw new CustomException(ErrorCode.TICKET_ALREADY_PROCESSED);
        }
        this.status = TicketPassStatus.ACTIVE;
        this.remainingCount = this.totalCount;
        this.activatedAt = now;
        this.paymentConfirmedAt = now;
        if (isPeriod()) {
            this.validUntil = now.toLocalDate().plusMonths(productOption.getPeriodMonths());
        }
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

    /** 신청 취소 시 1회 환불한다. 이미 가득 차 있거나 멤버십(횟수 없음)이면 무시. */
    public void refundOne() {
        if (isPeriod() || this.remainingCount >= this.totalCount) {
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

    public boolean isPeriod() {
        return productOption != null && productOption.getKind() == TicketProductKind.PERIOD;
    }

    /** 이 회차 날짜에 쓸 수 있는가. 멤버십은 만료일(포함)까지, 횟수권은 잔여 기준. */
    public boolean isUsableOn(LocalDate sessionDate) {
        if (!isPeriod()) {
            return isUsable();
        }
        return this.status == TicketPassStatus.ACTIVE && validUntil != null && !sessionDate.isAfter(validUntil);
    }

    /** 멤버십 기간 연장(매칭 실패 보상). */
    public void extend(int days) {
        this.validUntil = this.validUntil.plusDays(days);
        this.extendedDays += days;
    }

    /** 멤버십 기간 만료. */
    public void expire() {
        this.status = TicketPassStatus.USED_UP;
    }

    public void adjustRemaining(int quantity) {
        int adjusted = this.remainingCount + quantity;
        // 멤버십은 횟수가 없어 조정 대상이 아니다(잔여 0 조정이 USED_UP으로 만들어 버린다).
        if (isPeriod() || quantity == 0 || adjusted < 0) {
            throw new CustomException(ErrorCode.INVALID_TICKET_ADJUSTMENT);
        }
        this.remainingCount = adjusted;
        this.status = adjusted == 0 ? TicketPassStatus.USED_UP : TicketPassStatus.ACTIVE;
    }
}
