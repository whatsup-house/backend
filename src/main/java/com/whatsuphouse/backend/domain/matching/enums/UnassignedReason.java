package com.whatsuphouse.backend.domain.matching.enums;

/** 매칭 실행에서 테이블에 앉지 못한 사유. (설계 4.5-8) */
public enum UnassignedReason {
    AGE_GAP,
    BLOCKED_PAIR,
    NOT_ENOUGH_PEOPLE,
    LOW_SCORE,
    NEXT_SESSION_WAITING
}
