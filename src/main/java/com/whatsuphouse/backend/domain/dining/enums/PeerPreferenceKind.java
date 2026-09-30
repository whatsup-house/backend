package com.whatsuphouse.backend.domain.dining.enums;

/** 같은 테이블 멤버에 대한 선호. AGAIN은 이전 만남 페널티 면제, AVOID는 같은 테이블 금지(설계 4.3·4.4). */
public enum PeerPreferenceKind {
    AGAIN, AVOID
}
