package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.enums.CancelWindow;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 취소 시점 판정. 시각은 모두 Asia/Seoul 기준 LocalDateTime. (설계 4.1, KAN-402) */
public final class CancelPolicy {

    private CancelPolicy() {
    }

    // 마감 = 회차 날짜 D-1 00:00(목요일 회차면 화요일 24:00). 당일 = 회차 날짜 00:00 이후.
    public static CancelWindow evaluate(LocalDateTime now, LocalDate sessionDate) {
        if (now.isBefore(sessionDate.minusDays(1).atStartOfDay())) {
            return CancelWindow.BEFORE_DEADLINE;
        }
        if (now.isBefore(sessionDate.atStartOfDay())) {
            return CancelWindow.AFTER_DEADLINE;
        }
        return CancelWindow.SAME_DAY;
    }
}
