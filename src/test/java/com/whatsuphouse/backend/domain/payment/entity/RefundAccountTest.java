package com.whatsuphouse.backend.domain.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundAccountTest {

    @Test
    @DisplayName("세 값이 모두 채워져 있으면 완성된 계좌다")
    void refundAccount_isComplete_trueWhenAllFilled() {
        assertThat(new RefundAccount("우리", "1002-123", "홍길동").isComplete()).isTrue();
    }

    @Test
    @DisplayName("하나라도 비어 있으면 완성되지 않은 계좌다")
    void refundAccount_isComplete_falseWhenBlank() {
        assertThat(new RefundAccount("우리", "1002-123", " ").isComplete()).isFalse();
        assertThat(new RefundAccount("", "1002-123", "홍길동").isComplete()).isFalse();
        assertThat(new RefundAccount("우리", null, "홍길동").isComplete()).isFalse();
    }
}
