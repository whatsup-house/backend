package com.whatsuphouse.backend.domain.payment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
