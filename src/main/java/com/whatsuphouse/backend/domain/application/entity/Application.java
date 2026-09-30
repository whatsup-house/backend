package com.whatsuphouse.backend.domain.application.entity;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.enums.PaymentStatus;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
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

    // 모임 종류.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    // 배정 회차. V5 이전 신청은 옛 게더링 ID(=회차 ID)로 채워졌다. 우연한 식탁은 매칭 전 NULL일 수 있다. (KAN-337)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private GatheringSession session;

    // 우연한 식탁 매칭 진행 상태. 그 외 타입은 NULL.
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", length = 20)
    private MatchStatus matchStatus;

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

    // session이 있으면 종류는 그 회차의 종류. 회차 배정 전 우연한 식탁 신청은 session 없이 gathering만 넘긴다. (KAN-338)
    @Builder
    public Application(String bookingNumber, Gathering gathering, GatheringSession session, User user, String name,
                       String phone, String email, Map<String, Object> formSnapshot) {
        this.bookingNumber = bookingNumber;
        this.session = session;
        this.gathering = session != null ? session.getGathering() : gathering;
        // 우연한 식탁 신청은 매칭 대기로 시작한다.
        if (this.gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
            this.matchStatus = MatchStatus.WAITING;
        }
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

    /**
     * 참가 확정. 우연한 식탁은 생성 시점에 이미 WAITING이지만, match_status 도입(V5) 전 신청은 NULL일 수 있어
     * 확정 시점에 비어 있으면 WAITING으로 보정한다. 이미 매칭 상태가 있으면 건드리지 않는다. (KAN-342)
     */
    public void confirm() {
        this.status = ApplicationStatus.CONFIRMED;
        if (this.reviewedAt == null) {
            this.reviewedAt = LocalDateTime.now();
        }
        if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE && this.matchStatus == null) {
            this.matchStatus = MatchStatus.WAITING;
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

    public void changeMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    /** 대체 회차로 옮긴다(MatchResolution TRANSFER). 배정 회차를 비우고 매칭 대기로 돌아간다. 희망 회차 교체는 서비스가 한다. (KAN-347) */
    public void transfer() {
        this.session = null;
        this.matchStatus = MatchStatus.WAITING;
    }

    /** 우연한 식탁 테이블 확정: 그 테이블의 회차를 배정 회차로 두고 매칭 상태를 CONFIRMED로. (KAN-346) */
    public void confirmMatch(GatheringSession session) {
        this.session = session;
        this.matchStatus = MatchStatus.CONFIRMED;
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
        Integer price = getEffectivePrice();
        return price != null && price == 0;
    }

    // 일반 유료 게더링 여부. 우연한 식탁은 이용권 결제 축으로 처리하므로 여기서 제외한다. (KAN-289)
    public boolean isPaidGathering() {
        Integer price = getEffectivePrice();
        return gathering.getGatheringType() != GatheringType.RANDOM_TABLE && price != null && price > 0;
    }

    public boolean requiresRandomTableTicket() {
        Integer price = getEffectivePrice();
        return gathering.getGatheringType() == GatheringType.RANDOM_TABLE && (price == null || price > 0);
    }

    // 배정 회차의 가격. 회차 배정 전(우연한 식탁 매칭 전)이면 종류 기본 가격. (KAN-338)
    private Integer getEffectivePrice() {
        return session != null ? session.getEffectivePrice() : gathering.getBasePrice();
    }

    // 배정 회차가 취소됐는지. 배정 전이면 false(회차 취소 일괄 환불 대상이 아니었다).
    public boolean isSessionCancelled() {
        return session != null && session.getStatus() == GatheringSessionStatus.CANCELLED;
    }

    /**
     * 기존 응답의 gatheringId 자리 값. 배정 회차가 있으면 회차 ID(= 옛 게더링 ID), 배정 전이면 종류 ID.
     * GET /api/gatherings/{id}는 둘 다 종류로 해석한다. (KAN-338)
     */
    public UUID getLegacyGatheringId() {
        return session != null ? session.getId() : gathering.getId();
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
