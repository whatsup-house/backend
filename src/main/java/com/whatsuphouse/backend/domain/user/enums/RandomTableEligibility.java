package com.whatsuphouse.backend.domain.user.enums;

/**
 * 우연한 식탁 참여 자격. 회원 전용 전환에 따라 participant 도메인에서 user 도메인으로 이관.
 */
public enum RandomTableEligibility {
    UNREVIEWED,
    APPROVED,
    REJECTED,
    SUSPENDED
}
