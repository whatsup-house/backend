package com.whatsuphouse.backend.domain.notification.enums;

/**
 * 알림 클릭 시 이동 대상. FE가 각 값을 라우트로 매핑한다. (KAN-263)
 * APPLICATIONS=마이페이지 신청 내역, MILEAGE=마일리지 현황, REVIEWS=마이페이지 후기,
 * TICKET_PURCHASE=우연한 식탁 이용권 구매 (KAN-342), DINING_TABLE=우연한 식탁 테이블 상세(linkId = 테이블 ID) (KAN-346),
 * DINING_FEEDBACK=우연한 식탁 피드백 작성(linkId = 테이블 ID),
 * DINING_HISTORY=우연한 식탁 참가 이력(다음 모집 알림. linkId = 새 회차가 열린 모임 종류 ID, 모임 상세 이동용) (KAN-349)
 * 우연한 식탁 매칭 대기·재배치 알림은 APPLICATIONS에 linkId = 신청 ID를 채운다. (KAN-349)
 */
public enum NotificationLink {
    APPLICATIONS,
    MILEAGE,
    REVIEWS,
    TICKET_PURCHASE,
    DINING_TABLE,
    DINING_FEEDBACK,
    DINING_HISTORY
}
