package com.whatsuphouse.backend.domain.payment.enums;

/** 입금 알림 대사 결과. (KAN-403) */
public enum DepositMatchResult {
    MATCHED,                // 후보 1건 → 결제 완료
    IGNORED,                // 입금 알림 아님(출금 등). 기록만
    NO_CANDIDATE,           // 후보 없음(또는 정산 실패) → 예외함
    MULTIPLE_CANDIDATES,    // 후보 2건 이상 → 예외함
    PARSE_FAILED            // 입금 알림인데 이름·금액을 못 읽음 → 예외함
}
