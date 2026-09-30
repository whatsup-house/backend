package com.whatsuphouse.backend.domain.matching.enums;

/** 테이블 멤버가 그 테이블에 들어온 경위. SPLIT은 운영자 분리(KAN-347)용. */
public enum AssignReason {
    INITIAL,
    REALLOCATED,
    MANUAL,
    SPLIT,
    MERGED
}
