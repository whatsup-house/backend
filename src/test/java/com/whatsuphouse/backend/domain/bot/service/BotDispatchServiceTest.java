package com.whatsuphouse.backend.domain.bot.service;

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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotDispatchServiceTest {

    @Mock
    private BotSettingRepository botSettingRepository;

    @Mock
    private BotWebhookClient botWebhookClient;

    @InjectMocks
    private BotDispatchService botDispatchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(botDispatchService, "enabled", true);
        ReflectionTestUtils.setField(botDispatchService, "replyToken", "token");
        ReflectionTestUtils.setField(botDispatchService, "allowedUserIdsValue", "");
        given(botWebhookClient.isConfigured()).willReturn(true);
        given(botSettingRepository.findById(any())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("reply-url 호스트가 whatsup.house가 아니면 시작 시 봇을 끈다")
    void replyUrl_nonWhatsupHost_disablesBot() {
        // given
        ReflectionTestUtils.setField(botDispatchService, "replyUrl", "https://evil.example.com/api/bot/replies");

        // when
        botDispatchService.init();

        // then
        assertThat(botDispatchService.isActive()).isFalse();
    }

    @Test
    @DisplayName("reply-url이 https + whatsup.house 계열이면 봇이 켜진다")
    void replyUrl_whatsupHost_keepsBotActive() {
        // given
        ReflectionTestUtils.setField(botDispatchService, "replyUrl", "https://api.whatsup.house/api/bot/replies");

        // when
        botDispatchService.init();

        // then
        assertThat(botDispatchService.isActive()).isTrue();
    }

    @Test
    @DisplayName("https가 아니거나 호스트가 whatsup.house로 끝나는 척하는 URL은 거부한다")
    void isWhatsupHttpsUrl_rejectsLookalikes() {
        assertThat(BotDispatchService.isWhatsupHttpsUrl("https://whatsup.house/api/bot/replies")).isTrue();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("https://staging.whatsup.house/api/bot/replies")).isTrue();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("http://api.whatsup.house/api/bot/replies")).isFalse();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("https://evilwhatsup.house/api/bot/replies")).isFalse();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("https://whatsup.house.evil.com/api/bot/replies")).isFalse();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("https://user@evil.com/whatsup.house")).isFalse();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("")).isFalse();
        assertThat(BotDispatchService.isWhatsupHttpsUrl("not a url")).isFalse();
    }

    @Test
    @DisplayName("마스터 스위치가 꺼져 있으면 비활성")
    void isActive_masterSwitchOff_false() {
        // given
        ReflectionTestUtils.setField(botDispatchService, "replyUrl", "https://api.whatsup.house/api/bot/replies");
        ReflectionTestUtils.setField(botDispatchService, "enabled", false);
        botDispatchService.init();

        // when & then
        assertThat(botDispatchService.isActive()).isFalse();
    }
}
