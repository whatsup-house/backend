package com.whatsuphouse.backend.domain.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 응답 기한(respond_by)이 지난 해결 선택을 5분마다 만료 처리한다(이용권 보관으로 자동 처리). (설계 4.8, KAN-347) */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchResolutionExpiryScheduler {

    private final MatchResolutionService matchResolutionService;

    @Scheduled(cron = "0 */5 * * * *")
    public void expireResolutions() {
        for (UUID id : matchResolutionService.listExpiredResolutionIds()) {
            // 건별 트랜잭션: 한 건이 실패해도 나머지는 처리한다.
            try {
                matchResolutionService.expireResolution(id);
            } catch (RuntimeException e) {
                log.warn("[MatchResolution] 만료 처리 실패: resolutionId={}", id, e);
            }
        }
    }
}
