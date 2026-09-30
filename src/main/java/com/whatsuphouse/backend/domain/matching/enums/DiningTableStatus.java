package com.whatsuphouse.backend.domain.matching.enums;

/** 테이블 상태. PROPOSED → (유예 후) CONFIRMED → DONE, 재실행·해체 시 DISSOLVED. (설계 3장) */
public enum DiningTableStatus {
    PROPOSED,
    CONFIRMED,
    DONE,
    DISSOLVED
}
