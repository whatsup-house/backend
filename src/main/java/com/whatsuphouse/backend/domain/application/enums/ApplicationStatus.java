package com.whatsuphouse.backend.domain.application.enums;

import java.util.List;

public enum ApplicationStatus {
    PENDING, PAYMENT_PENDING, CONFIRMED, REJECTED, CANCELLED, ATTENDED;

    // 정원을 차지하는 상태: 관리자 승인(CONFIRMED) + 출석(ATTENDED). PENDING/CANCELLED 제외. (KAN-236)
    public static final List<ApplicationStatus> SEAT_OCCUPYING = List.of(CONFIRMED, ATTENDED);
}
