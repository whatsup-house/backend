package com.whatsuphouse.backend.domain.matching.scheduler;

import com.whatsuphouse.backend.domain.matching.service.DiningConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 유예가 끝난(confirm_at ≤ now) 제안 테이블을 1분마다 자동 확정한다. 테이블마다 별도 트랜잭션. (설계 4.6, KAN-346)
 * 확정됐는데 채팅방이 없는 테이블은 5분마다 채팅방 생성을 다시 시도한다(테이블마다 최대 3회). (KAN-438)
 */
@Component
@RequiredArgsConstructor
public class DiningConfirmScheduler {

    private final DiningConfirmService diningConfirmService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void confirmDueTables() {
        diningConfirmService.confirmDueTables();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void retryMissingChatRooms() {
        diningConfirmService.retryMissingChatRooms();
    }
}
