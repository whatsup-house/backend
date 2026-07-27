package com.whatsuphouse.backend.domain.notification.enums;

/**
 * 알림 클릭 시 이동 대상. FE가 각 값을 라우트로 매핑한다. (KAN-263)
 * APPLICATIONS=마이페이지 신청 내역, MILEAGE=마일리지 현황, REVIEWS=마이페이지 후기
 */
public enum NotificationLink {
    APPLICATIONS,
    MILEAGE,
    REVIEWS
}
