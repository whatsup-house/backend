package com.whatsuphouse.backend.domain.matching.scheduler;

import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.service.DiningMatchService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 매칭 시각(match_run_at)이 된 모집 중 우연한 식탁 회차를 마감(CLOSED)하고 매칭을 실행한다. (설계 4.1, KAN-346)
 * 중복 실행 방지: 마감은 SKIP LOCKED로 잠근 트랜잭션이 커밋하므로 한 회차는 한 인스턴스만 잡는다.
 * 이미 매칭 실행 기록이 있는 회차(마감 전 수동 실행 등)는 마감만 하고 다시 돌리지 않는다.
 * 매칭 실행은 회차마다 별도 트랜잭션이라 한 회차의 실패가 다른 회차를 막지 않는다. 실패한 회차는 CLOSED로 남으니 운영자가 수동 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiningMatchScheduler {

    private final GatheringService gatheringService;
    private final MatchingService matchingService;
    private final DiningMatchService diningMatchService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void runDueMatches() {
        List<UUID> sessionIds = gatheringService.closeDueRandomTableSessions(LocalDateTime.now());
        if (sessionIds.isEmpty()) {
            return;
        }
        Set<UUID> alreadyRun = matchingService.findSessionIdsWithMatchRun(sessionIds);
        for (UUID sessionId : sessionIds) {
            if (alreadyRun.contains(sessionId)) {
                continue;
            }
            try {
                diningMatchService.runMatch(sessionId, MatchRunTrigger.SCHEDULED, null, null);
            } catch (Exception e) {
                log.error("회차 자동 매칭 실패: sessionId={}", sessionId, e);
            }
        }
    }
}
