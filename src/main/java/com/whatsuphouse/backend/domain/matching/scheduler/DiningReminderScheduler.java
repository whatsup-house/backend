package com.whatsuphouse.backend.domain.matching.scheduler;

import com.whatsuphouse.backend.domain.matching.service.DiningReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 10분마다: 회차 시작 23~25시간 전 확정 테이블 리마인드, 회차 시작 ±10분 확정 테이블 대화 콘텐츠 게시. 테이블마다 1회. (설계 4.9, KAN-349)
 * 창(리마인드 2시간, 콘텐츠 20분)이 주기보다 넓어 모든 테이블이 적어도 한 번은 창 안에서 잡힌다.
 */
@Component
@RequiredArgsConstructor
public class DiningReminderScheduler {

    private final DiningReminderService diningReminderService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void run() {
        diningReminderService.sendDueReminders();
        diningReminderService.postDueContents();
    }
}
