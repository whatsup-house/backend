package com.whatsuphouse.backend.domain.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 그록봇 대기열 스케줄(KAN-406). 서버 마스터 스위치(bot.enabled)가 꺼져 있으면 아무것도 하지 않는다
 * (봇을 쓰지 않는 환경·테스트에서 2초 폴링 쿼리를 막는다).
 * - run: lease 만료 처리(다시 PENDING / 3회면 FAILED + 이관)와 5분 미처리 PENDING 이관. 관리자 토글과 무관하게 돈다.
 * - signal: 봇이 활성일 때만 Slack 신호. 재시도 대기(최대 십수 초)가 만료·이관 처리를 밀지 않게 따로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotDispatchScheduler {

    private final BotDispatchService botDispatchService;
    private final BotSignalService botSignalService;

    @Scheduled(fixedDelay = 2_000, initialDelay = 2_000)
    public void run() {
        if (!botDispatchService.isMasterEnabled()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            botDispatchService.expireLeases(now);
            botDispatchService.handOffUnclaimed(now);
        } catch (Exception e) {
            log.error("그록봇 대기열 스케줄 실패", e);
        }
    }

    @Scheduled(fixedDelay = 2_000, initialDelay = 2_000)
    public void signal() {
        if (!botDispatchService.isMasterEnabled() || !botDispatchService.isActive()) {
            return;
        }
        try {
            botSignalService.signalIfDue(LocalDateTime.now());
        } catch (Exception e) {
            log.error("그록봇 Slack 신호 스케줄 실패", e);
        }
    }
}
