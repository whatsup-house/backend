package com.whatsuphouse.backend.domain.user.enums;

/**
 * 우연한 식탁 참여 자격. 회원 전용 전환에 따라 participant 도메인에서 user 도메인으로 이관.
 */
public enum RandomTableEligibility {
    UNREVIEWED,
    APPROVED,
    REJECTED,
    SUSPENDED,
    // 운영자 안전 조치(RESTRICT/BAN)로 제한. 예외함 SAFETY 처리에서만 설정된다. (KAN-348)
    RESTRICTED
}
