package com.whatsuphouse.backend.domain.application.event;

import com.whatsuphouse.backend.domain.application.entity.Application;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신청 상태·매칭 상태·결제(이용권 차감·환불, 보증금, 입금 확인)를 바꾼 서비스가 바꾼 뒤 발행한다.
 * 상태 변경 이력(ApplicationStatusLogService)이 같은 트랜잭션에서 마지막 기록과 비교해 바뀐 항목만 남긴다. (KAN-437)
 * reason은 이력 사유(200자까지).
 */
@Getter
@RequiredArgsConstructor
public class ApplicationStatusChangedEvent {
    private final Application application;
    private final String reason;
}
