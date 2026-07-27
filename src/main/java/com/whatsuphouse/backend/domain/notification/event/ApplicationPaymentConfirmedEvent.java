package com.whatsuphouse.backend.domain.notification.event;

import com.whatsuphouse.backend.domain.application.entity.Application;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관리자가 입금을 확인(체크)한 시점에 발행된다. 신청자에게 입금 완료 안내 메일을 보낸다. (KAN-242)
 * 입금 확인 해제(uncheck)나 이미 확인된 신청의 재확인에는 발행되지 않는다.
 */
@Getter
@RequiredArgsConstructor
public class ApplicationPaymentConfirmedEvent {
    private final Application application;
}
