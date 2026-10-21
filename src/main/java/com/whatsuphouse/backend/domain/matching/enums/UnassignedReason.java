package com.whatsuphouse.backend.domain.matching.enums;

/** 매칭 실행에서 테이블에 앉지 못한 사유. (설계 4.5-8) */
public enum UnassignedReason {
    AGE_GAP,
    BLOCKED_PAIR,
    NOT_ENOUGH_PEOPLE,
    /** 더 이상 만들지 않는다(KAN-420). 기존 match_runs.unassigned_reasons JSON 역직렬화용으로만 남긴다. */
    @Deprecated
    LOW_SCORE,
    NEXT_SESSION_WAITING,
    /** 희망 지역의 테이블이 최소 테이블 수(minTablesPerRegion)에 못 미쳐 그 지역 결과를 버렸다(KAN-431). */
    REGION_TOO_SMALL
}
