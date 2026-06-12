package com.whatsuphouse.backend.domain.application.enums;

/**
 * 신청자에게 노출하는 입금 상태. 신청 상태(ApplicationStatus)와 독립된 별개 축이다.
 * 무료 게더링은 입금 개념이 없으므로 응답에서 null로 표현(표시하지 않음)한다.
 */
public enum PaymentStatus {
    PENDING,    // 입금 확인 중
    CONFIRMED   // 입금 완료
}
