package com.whatsuphouse.backend.domain.notification.enums;

public enum NotificationType {
    PARTICIPATION_CONFIRMED,  // 참가 확정
    MILEAGE_EARNED,           // 마일리지 적립
    REVIEW_LIKE_MILESTONE,    // 후기 좋아요 마일스톤
    DINING_PAYMENT_PENDING,   // 우연한 식탁 이용권 결제 대기 (KAN-342)
    DINING_CONFIRMED,         // 우연한 식탁 테이블 확정 (KAN-346)
    DINING_ALTERNATIVE_OFFERED, // 우연한 식탁 매칭 실패 → 해결 선택 요청 (KAN-347)
    DINING_TRANSFERRED,       // 우연한 식탁 대체 회차 이동 완료 (KAN-347)
    DINING_REFUND_REQUESTED,  // 우연한 식탁 환불 요청 접수 (KAN-347)
    DINING_REFUND_COMPLETED,  // 우연한 식탁 환불 완료 (KAN-347)
    DINING_WAITING,           // 우연한 식탁 결제 완료·매칭 대기 (KAN-349)
    DINING_REALLOCATING,      // 우연한 식탁 다음 희망 회차로 재배치 대기 (KAN-349)
    DINING_REMINDER,          // 우연한 식탁 회차 24시간 전 리마인드 (KAN-349)
    DINING_FEEDBACK_REQUEST,  // 우연한 식탁 회차 종료 후 피드백 요청 (KAN-349)
    DINING_NEXT_SESSIONS      // 우연한 식탁 새 회차 모집 (KAN-349)
}
