package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.service.CancelPolicy;
import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CancelPolicyTest {

    // 목요일 회차: 마감 = 화요일 24:00(= 수요일 00:00), 당일 = 목요일 00:00부터.
    private final LocalDate thursday = LocalDate.of(2026, 10, 22);

    @Test
    @DisplayName("마감(전날 0시) 직전은 BEFORE_DEADLINE")
    void beforeDeadline() {
        assertThat(CancelPolicy.evaluate(LocalDateTime.of(2026, 10, 20, 23, 59), thursday))
                .isEqualTo(CancelWindow.BEFORE_DEADLINE);
    }

    @Test
    @DisplayName("마감 시각부터 전날 끝까지는 AFTER_DEADLINE")
    void afterDeadline() {
        assertThat(CancelPolicy.evaluate(LocalDateTime.of(2026, 10, 21, 0, 0), thursday))
                .isEqualTo(CancelWindow.AFTER_DEADLINE);
        assertThat(CancelPolicy.evaluate(LocalDateTime.of(2026, 10, 21, 0, 1), thursday))
                .isEqualTo(CancelWindow.AFTER_DEADLINE);
        assertThat(CancelPolicy.evaluate(LocalDateTime.of(2026, 10, 21, 23, 59, 59), thursday))
                .isEqualTo(CancelWindow.AFTER_DEADLINE);
    }

    @Test
    @DisplayName("회차 날짜 0시부터는 SAME_DAY")
    void sameDay() {
        assertThat(CancelPolicy.evaluate(LocalDateTime.of(2026, 10, 22, 0, 0), thursday))
                .isEqualTo(CancelWindow.SAME_DAY);
    }
}
