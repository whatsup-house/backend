package com.whatsuphouse.backend.domain.payment.entity;

import jakarta.persistence.Embeddable;
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

    private String bank;
    private String accountNumber;
    private String holder;

    public boolean isComplete() {
        return hasText(bank) && hasText(accountNumber) && hasText(holder);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
