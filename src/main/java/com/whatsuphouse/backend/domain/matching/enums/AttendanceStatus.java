package com.whatsuphouse.backend.domain.matching.enums;

/** 확정 테이블 멤버의 참석 상태. 확정 시 SCHEDULED로 만들어진다. (설계 2.6, 3장) */
public enum AttendanceStatus {
    SCHEDULED,
    ATTENDED,
    CANCELED_EARLY,
    CANCELED_LATE,
    NO_SHOW
}
