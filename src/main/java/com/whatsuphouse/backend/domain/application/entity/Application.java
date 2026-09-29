package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.PaymentStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 20)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    // 신청 주체. 회원이면 user 연결, 비회원 신청은 NULL(이름/연락처는 아래 스냅샷 필드 사용).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 11)
    private String phone;

    // 알림 발송용 이메일. 회원=계정 이메일, 비회원=신청서 답변 이메일. 기존 데이터 호환을 위해 nullable.
    @Column(length = 255)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> formSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    // 관리자가 입금을 확인한 시각. NULL=입금 확인 중, 값 있으면 입금 완료. 유료 게더링에서만 의미를 가진다. (KAN-242)
    @Column(name = "payment_confirmed_at")
    private LocalDateTime paymentConfirmedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Builder
    public Application(String bookingNumber, Gathering gathering, User user, String name, String phone,
                       String email, Map<String, Object> formSnapshot) {
        this.bookingNumber = bookingNumber;
        this.gathering = gathering;
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.formSnapshot = formSnapshot;
        this.status = ApplicationStatus.PENDING;
    }

    public void cancel() {
        this.status = ApplicationStatus.CANCELLED;
        delete();
    }

    public void confirm() {
        this.status = ApplicationStatus.CONFIRMED;
        if (this.reviewedAt == null) {
            this.reviewedAt = LocalDateTime.now();
        }
    }

    public void awaitPayment() {
        this.status = ApplicationStatus.PAYMENT_PENDING;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = null;
    }

    public void reject(String reason) {
        this.status = ApplicationStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public void attend() {
        this.status = ApplicationStatus.ATTENDED;
    }

    // 입금 확인 처리. 신청 상태(status)와 독립적으로 토글된다. (KAN-242)
    public void confirmPayment() {
        this.paymentConfirmedAt = LocalDateTime.now();
    }

    public void cancelPayment() {
        this.paymentConfirmedAt = null;
    }

    public boolean isFreeGathering() {
        Integer price = gathering.getPrice();
        return price != null && price == 0;
    }

    // 일반 유료 게더링 여부. 우연한 식탁은 이용권 결제 축으로 처리하므로 여기서 제외한다. (KAN-289)
    public boolean isPaidGathering() {
        Integer price = gathering.getPrice();
        return gathering.getGatheringType() != GatheringType.RANDOM_TABLE && price != null && price > 0;
    }

    public boolean requiresRandomTableTicket() {
        Integer price = gathering.getPrice();
        return gathering.getGatheringType() == GatheringType.RANDOM_TABLE && (price == null || price > 0);
    }

    public boolean isPaymentConfirmed() {
        return paymentConfirmedAt != null;
    }

    // 신청자 노출용 결제 상태. 유료 우연한 식탁은 이용권 도메인에서 상태를 보여주므로 null을 반환한다.
    public PaymentStatus getPaymentStatus() {
        if (isFreeGathering()) {
            return PaymentStatus.FREE;
        }
        if (!isPaidGathering()) {
            return null;
        }
        return paymentConfirmedAt != null ? PaymentStatus.CONFIRMED : PaymentStatus.PENDING;
    }
}
