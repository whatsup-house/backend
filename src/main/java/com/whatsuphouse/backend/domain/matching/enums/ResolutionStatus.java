package com.whatsuphouse.backend.domain.matching.enums;

/** 해결 선택 상태. OFFERED(응답 대기) → RESOLVED(참가자 선택) | EXPIRED(기한 경과, 이용권 보관으로 자동 처리). */
public enum ResolutionStatus {
    OFFERED,
    RESOLVED,
    EXPIRED
}
