package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.bot.entity.BotSetting;
import com.whatsuphouse.backend.domain.bot.repository.BotDispatchRepository;
import com.whatsuphouse.backend.domain.bot.repository.BotSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotDispatchServiceTest {

    @Mock
    private BotSettingRepository botSettingRepository;

    @Mock
    private BotDispatchRepository botDispatchRepository;

    @InjectMocks
    private BotDispatchService botDispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(botDispatchService, "enabled", true);
        ReflectionTestUtils.setField(botDispatchService, "replyToken", "token");
        ReflectionTestUtils.setField(botDispatchService, "allowedUserIdsValue", "");
        given(botSettingRepository.findById(any())).willReturn(Optional.empty());
        botDispatchService.init();
    }

    @Test
    @DisplayName("마스터 스위치·토큰·토글이 켜져 있으면 활성(Slack 신호 주소는 필요 없음)")
    void isActive_allOn_true() {
        assertThat(botDispatchService.isActive()).isTrue();
    }

    @Test
    @DisplayName("마스터 스위치가 꺼져 있으면 비활성")
    void isActive_masterSwitchOff_false() {
        ReflectionTestUtils.setField(botDispatchService, "enabled", false);

        assertThat(botDispatchService.isActive()).isFalse();
    }

    @Test
    @DisplayName("봇 토큰이 없으면 비활성")
    void isActive_noReplyToken_false() {
        ReflectionTestUtils.setField(botDispatchService, "replyToken", "");

        assertThat(botDispatchService.isActive()).isFalse();
    }

    @Test
    @DisplayName("관리자 전역 토글이 꺼져 있으면 비활성이고 대기 문의 조회는 lease 없이 빈 목록")
    void listPending_toggleOff_emptyWithoutLease() {
        // given
        BotSetting off = BotSetting.initial();
        off.change(false, null);
        given(botSettingRepository.findById(any())).willReturn(Optional.of(off));

        // when & then
        assertThat(botDispatchService.listPending(10).getItems()).isEmpty();
        then(botDispatchRepository).should(never()).lockDueIds(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
