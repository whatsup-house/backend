package com.whatsuphouse.backend.domain.notification.enums;

/**
 * 알림 클릭 시 이동 대상. FE가 각 값을 라우트로 매핑한다. (KAN-263)
 * APPLICATIONS=마이페이지 신청 내역, MILEAGE=마일리지 현황, REVIEWS=마이페이지 후기,
 * TICKET_PURCHASE=우연한 식탁 이용권 구매 (KAN-342),
 * DINING_RESOLUTION=우연한 식탁 해결 선택(대체 일정·이용권 보관·환불). 대상 ID는 내 신청 조회의 resolutionId (KAN-347)
 */
public enum NotificationLink {
    APPLICATIONS,
    MILEAGE,
    REVIEWS,
    TICKET_PURCHASE,
    DINING_RESOLUTION
}
