package com.whatsuphouse.backend.domain.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 2초마다 발송 대기(디바운스·재시도 끝) 건을 보내고, 3분 무응답 SENT를 이관한다. 봇이 꺼져 있으면 아무것도 안 한다. (KAN-406) */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotDispatchScheduler {

    private final BotDispatchService botDispatchService;

    @Scheduled(fixedDelay = 2_000, initialDelay = 2_000)
    public void run() {
        if (!botDispatchService.isActive()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            botDispatchService.sendDue(now);
            botDispatchService.handOffTimedOut(now);
        } catch (Exception e) {
            log.error("그록봇 발송 스케줄 실패", e);
        }
    }
}
