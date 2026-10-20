package com.whatsuphouse.backend.domain.bot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BotDispatchSchedulerTest {

    @Mock
    private BotDispatchService botDispatchService;

    @Mock
    private BotSignalService botSignalService;

    @InjectMocks
    private BotDispatchScheduler botDispatchScheduler;

    @Test
    @DisplayName("마스터 스위치만 켜져 있으면 lease 만료·미처리 이관은 토글과 무관하게 돈다")
    void run_masterOn_expiresAndHandsOff() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(true);

        // when
        botDispatchScheduler.run();

        // then
        then(botDispatchService).should().expireLeases(any());
        then(botDispatchService).should().handOffUnclaimed(any());
        then(botDispatchService).should(never()).isActive();
    }

    @Test
    @DisplayName("봇이 비활성(토글 OFF·토큰 없음)이면 Slack 신호를 보내지 않는다")
    void signal_inactive_noSignal() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(true);
        given(botDispatchService.isActive()).willReturn(false);

        // when
        botDispatchScheduler.signal();

        // then
        then(botSignalService).should(never()).signalIfDue(any());
    }

    @Test
    @DisplayName("활성이면 Slack 신호 판단을 돈다")
    void signal_active_signals() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(true);
        given(botDispatchService.isActive()).willReturn(true);

        // when
        botDispatchScheduler.signal();

        // then
        then(botSignalService).should().signalIfDue(any());
    }

    @Test
    @DisplayName("서버 마스터 스위치가 꺼져 있으면 아무것도 하지 않는다")
    void run_masterOff_noop() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(false);

        // when
        botDispatchScheduler.run();
        botDispatchScheduler.signal();

        // then
        then(botDispatchService).should(never()).expireLeases(any());
        then(botDispatchService).should(never()).handOffUnclaimed(any());
        then(botSignalService).should(never()).signalIfDue(any());
    }
}
