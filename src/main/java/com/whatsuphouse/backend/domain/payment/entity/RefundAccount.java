package com.whatsuphouse.backend.domain.payment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 환불 계좌. 컬럼명은 사용하는 엔티티가 @AttributeOverrides로 정한다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefundAccount {

    // 컬럼 길이(deposits.return_* VARCHAR(50))와 맞춘다. 요청 DTO에서 @Valid로 400 처리.
    @Size(max = 50)
    private String bank;
    @Size(max = 50)
    private String accountNumber;
    @Size(max = 50)
    private String holder;

    @JsonIgnore
    public boolean isComplete() {
        return hasText(bank) && hasText(accountNumber) && hasText(holder);
    }

    /** 환불 송금에 쓸 계좌가 다 채워졌는지 확인한다. 아니면 REFUND_ACCOUNT_REQUIRED. (KAN-404) */
    public static RefundAccount requireComplete(RefundAccount account) {
        if (account == null || !account.isComplete()) {
            throw new CustomException(ErrorCode.REFUND_ACCOUNT_REQUIRED);
        }
        return account;
    }

    /** 계좌번호를 끝 4자리만 남기고 가린 사본(송금 30일 후 보관용). 4자리 이하면 그대로. */
    public RefundAccount masked() {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return new RefundAccount(bank, accountNumber, holder);
        }
        int hidden = accountNumber.length() - 4;
        return new RefundAccount(bank, "*".repeat(hidden) + accountNumber.substring(hidden), holder);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
