package com.whatsuphouse.backend.domain.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * 입금 알림 대사 결과를 관리자에게 알린다. matched = 결제 대기 건과 연결됨(manual = 운영자 수동 연결), 아니면 예외함.
 * 원문·계좌번호는 싣지 않는다. 파싱 실패면 payerName·amount가 null. (KAN-416)
 */
@Getter
@RequiredArgsConstructor
public class AdminDepositNotifiedEvent {
    private final UUID depositNotificationId;
    private final boolean matched;
    private final boolean manual;
    private final String payerName;
    private final Integer amount;
}
