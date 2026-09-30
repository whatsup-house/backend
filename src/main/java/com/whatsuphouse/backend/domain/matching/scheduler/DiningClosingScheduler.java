package com.whatsuphouse.backend.domain.matching.scheduler;

import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 10분마다: 종료 + 1시간이 지난 회차의 노쇼 후보 표시·테이블 DONE·피드백 요청·회차 DONE. 회차마다 1회. (설계 4.9, KAN-349) */
@Component
@RequiredArgsConstructor
public class DiningClosingScheduler {

    private final DiningAttendanceService diningAttendanceService;

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void closeFinishedSessions() {
        diningAttendanceService.closeFinishedSessions();
    }
}
