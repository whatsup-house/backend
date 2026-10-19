package com.whatsuphouse.backend.domain.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 2초마다 발송 대기(디바운스·재시도 끝) 건을 보내고, 3분 무응답 SENT를 이관한다. (KAN-406)
 * 발송은 봇이 활성일 때만. 무응답 이관은 관리자 토글을 끄거나 설정이 빠져도 계속 돈다(이미 보낸 건이 SENT로 남지 않게).
 * 서버 마스터 스위치(bot.enabled)가 꺼져 있으면 둘 다 건너뛴다(봇을 쓰지 않는 환경·테스트에서 2초 폴링 쿼리를 막는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotDispatchScheduler {

    private final BotDispatchService botDispatchService;

    @Scheduled(fixedDelay = 2_000, initialDelay = 2_000)
    public void run() {
        if (!botDispatchService.isMasterEnabled()) {
            return;
        }
        try {
            if (botDispatchService.isActive()) {
                botDispatchService.sendDue(LocalDateTime.now());
            }
            botDispatchService.handOffTimedOut(LocalDateTime.now());
        } catch (Exception e) {
            log.error("그록봇 발송 스케줄 실패", e);
        }
    }
}
