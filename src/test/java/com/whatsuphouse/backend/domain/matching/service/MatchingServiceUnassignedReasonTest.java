package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.MatchRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

/** 참가자용 미배정 사유(MAT-12): 신청마다 희망 회차들의 가장 최근 실행만 본다. (KAN-436) */
@ExtendWith(MockitoExtension.class)
class MatchingServiceUnassignedReasonTest {

    @Mock
    private GatheringService gatheringService;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private DiningTableRepository diningTableRepository;
    @Mock
    private DiningTableMemberRepository diningTableMemberRepository;
    @Mock
    private MatchRunRepository matchRunRepository;
    @Mock
    private DiningMatchService diningMatchService;
    @Mock
    private DiningAttendanceService diningAttendanceService;

    @InjectMocks
    private MatchingService matchingService;

    private static MatchRun run(UUID sessionId, LocalDateTime startedAt, MatchRun.Unassigned... unassigned) {
        MatchRun run = MatchRun.start(sessionId, MatchRunTrigger.SCHEDULED, null, MatchingEngine.ALGORITHM_VERSION);
        ReflectionTestUtils.setField(run, "startedAt", startedAt);
        run.finish(0, 0, 0, 0, 0, List.of(unassigned));
        return run;
    }

    @Test
    @DisplayName("앞 회차에서 미배정됐어도 더 최근 회차 실행에서 앉았으면 사유 없음, 최근 실행에서 미배정이면 그 사유")
    void findLastUnassignedReasons_usesLatestRunOnly() {
        // given: moved는 A에서 다음 회차 대기 → B에서 착석. waiting은 A에서 다음 회차 대기, B는 희망하지 않음.
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        UUID moved = UUID.randomUUID();
        UUID waiting = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        given(matchRunRepository.findBySessionIdIn(anyCollection())).willReturn(List.of(
                run(sessionA, now.minusDays(2),
                        new MatchRun.Unassigned(moved, UnassignedReason.NEXT_SESSION_WAITING),
                        new MatchRun.Unassigned(waiting, UnassignedReason.AGE_GAP)),
                run(sessionB, now.minusDays(1))));

        // when
        Map<UUID, UnassignedReason> reasons = matchingService.findLastUnassignedReasons(Map.of(
                moved, List.of(sessionA, sessionB),
                waiting, List.of(sessionA)));

        // then
        assertThat(reasons).containsExactlyEntriesOf(Map.of(waiting, UnassignedReason.AGE_GAP));
    }
}
