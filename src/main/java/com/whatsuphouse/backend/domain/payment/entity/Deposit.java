package com.whatsuphouse.backend.domain.payment.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** 노쇼 보증금. 보증금 대상 회원의 우연한 식탁 신청 1건에 1행. (설계 4.3, KAN-402) */
@Entity
@Table(name = "deposits")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deposit {

    public static final int AMOUNT = 50_000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositStatus status;

    // 모든 컬럼이 NULL이면 Hibernate가 null로 읽는다.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "bank", column = @Column(name = "return_bank", length = 50)),
            @AttributeOverride(name = "accountNumber", column = @Column(name = "return_account", length = 50)),
            @AttributeOverride(name = "holder", column = @Column(name = "return_holder", length = 50))
    })
    private RefundAccount returnAccount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Deposit pending(Application application, RefundAccount returnAccount) {
        Deposit deposit = new Deposit();
        deposit.application = application;
        deposit.amount = AMOUNT;
        deposit.status = DepositStatus.PENDING;
        deposit.returnAccount = returnAccount;
        return deposit;
    }

    /** 입금 확인. PENDING일 때만(재처리는 무시). */
    public void markPaid() {
        if (status == DepositStatus.PENDING) {
            status = DepositStatus.PAID;
        }
    }

    /** 반환 요청. 받은 돈(PAID)만 돌려준다. 미입금·이미 처리된 건은 그대로. */
    public void requestReturn() {
        if (status == DepositStatus.PAID) {
            status = DepositStatus.RETURN_PENDING;
        }
    }

    /** 운영자 송금 완료(RETURN_PENDING → RETURNED). 그 외는 그대로. (KAN-404) */
    public void markReturned() {
        if (status == DepositStatus.RETURN_PENDING) {
            status = DepositStatus.RETURNED;
        }
    }

    /** 반환 송금 30일 후 계좌번호를 끝 4자리만 남긴다. */
    public void maskReturnAccount() {
        if (returnAccount != null) {
            returnAccount = returnAccount.masked();
        }
    }

    /** 몰수. 받은 돈이 아직 회원에게 돌아가지 않았을 때만(PAID·RETURN_PENDING). */
    public void forfeit() {
        if (status == DepositStatus.PAID || status == DepositStatus.RETURN_PENDING) {
            status = DepositStatus.FORFEITED;
        }
    }

    /** 신청 확정·매칭 조건 충족(입금 확인 이후 상태). */
    public boolean isSatisfied() {
        return status != DepositStatus.PENDING;
    }
}
