package com.whatsuphouse.backend.domain.application.enums;

/**
 * 신청자에게 노출하는 결제 상태. 신청 상태(ApplicationStatus)와 독립된 별개 축이다.
 * 우연한 식탁의 유료 이용권 결제는 별도 도메인에서 처리하므로 여기서는 무료 여부만 노출한다.
 */
public enum PaymentStatus {
    PENDING,    // 입금 확인 중
    CONFIRMED,  // 입금 완료
    FREE        // 결제 없음
}
