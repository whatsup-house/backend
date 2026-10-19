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

    @InjectMocks
    private BotDispatchScheduler botDispatchScheduler;

    @Test
    @DisplayName("봇이 비활성(토글 OFF·설정 누락)이어도 무응답 이관은 돌고 발송만 건너뛴다")
    void run_inactive_onlyTimeoutHandOff() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(true);
        given(botDispatchService.isActive()).willReturn(false);

        // when
        botDispatchScheduler.run();

        // then
        then(botDispatchService).should(never()).sendDue(any());
        then(botDispatchService).should().handOffTimedOut(any());
    }

    @Test
    @DisplayName("활성이면 발송과 무응답 이관을 모두 돈다")
    void run_active_sendsAndHandsOff() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(true);
        given(botDispatchService.isActive()).willReturn(true);

        // when
        botDispatchScheduler.run();

        // then
        then(botDispatchService).should().sendDue(any());
        then(botDispatchService).should().handOffTimedOut(any());
    }

    @Test
    @DisplayName("서버 마스터 스위치가 꺼져 있으면 아무것도 하지 않는다")
    void run_masterOff_noop() {
        // given
        given(botDispatchService.isMasterEnabled()).willReturn(false);

        // when
        botDispatchScheduler.run();

        // then
        then(botDispatchService).should(never()).sendDue(any());
        then(botDispatchService).should(never()).handOffTimedOut(any());
    }
}
