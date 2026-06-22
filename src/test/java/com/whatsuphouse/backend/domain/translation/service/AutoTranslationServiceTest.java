package com.whatsuphouse.backend.domain.translation.service;

import com.whatsuphouse.backend.domain.translation.client.GmsTranslationClient;
import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import com.whatsuphouse.backend.domain.translation.repository.ContentTranslationRepository;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AutoTranslationServiceTest {

    @Mock
    private GmsTranslationClient client;

    @Mock
    private ContentTranslationService translationService;

    @Mock
    private ContentTranslationRepository translationRepository;

    @InjectMocks
    private AutoTranslationService autoTranslationService;

    private UUID entityId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        ReflectionTestUtils.setField(autoTranslationService, "maxInputChars", 2000);
    }

    @Test
    @DisplayName("GMS 비활성(키 미설정/만료) → 번역 호출·저장 없이 스킵")
    void translate_disabled_doesNothing() {
        // given
        given(client.isEnabled()).willReturn(false);

        // when
        autoTranslationService.translate(TranslatableType.GATHERING, entityId, Map.of("title", "안녕하세요"));

        // then
        then(client).should(never()).translate(any(), any());
        then(translationService).should(never())
                .upsert(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("신규 필드 → en/ja 번역 후 DONE으로 upsert")
    void translate_newField_upsertsDone() {
        // given
        given(client.isEnabled()).willReturn(true);
        given(translationRepository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(
                any(), any(), any(), any())).willReturn(Optional.empty());
        given(client.translate(eq("안녕하세요"), any())).willReturn("Hello");

        // when
        autoTranslationService.translate(TranslatableType.GATHERING, entityId, Map.of("title", "안녕하세요"));

        // then
        then(translationService).should().upsert(eq(TranslatableType.GATHERING), eq(entityId), eq("title"),
                eq(AppLocale.EN), eq("Hello"), eq(TranslationStatus.DONE), anyString(), eq(false));
        then(translationService).should().upsert(eq(TranslatableType.GATHERING), eq(entityId), eq("title"),
                eq(AppLocale.JA), eq("Hello"), eq(TranslationStatus.DONE), anyString(), eq(false));
    }

    @Test
    @DisplayName("관리자 수동 보정(override) 번역은 자동 재번역하지 않음")
    void translate_override_skips() {
        // given
        given(client.isEnabled()).willReturn(true);
        ContentTranslation overridden = ContentTranslation.builder()
                .entityType(TranslatableType.GATHERING).entityId(entityId).field("title")
                .locale(AppLocale.EN.getCode()).value("manual").status(TranslationStatus.DONE)
                .sourceHash("old").isOverride(true).build();
        given(translationRepository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(
                any(), any(), any(), any())).willReturn(Optional.of(overridden));

        // when
        autoTranslationService.translate(TranslatableType.GATHERING, entityId, Map.of("title", "안녕하세요"));

        // then
        then(client).should(never()).translate(any(), any());
        then(translationService).should(never())
                .upsert(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("원문 미변경(해시 동일 + DONE) → 재번역하지 않음")
    void translate_unchangedHash_skips() {
        // given
        given(client.isEnabled()).willReturn(true);
        String ko = "안녕하세요";
        ContentTranslation done = ContentTranslation.builder()
                .entityType(TranslatableType.GATHERING).entityId(entityId).field("title")
                .locale(AppLocale.EN.getCode()).value("Hello").status(TranslationStatus.DONE)
                .sourceHash(sha256(ko)).isOverride(false).build();
        given(translationRepository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(
                any(), any(), any(), any())).willReturn(Optional.of(done));

        // when
        autoTranslationService.translate(TranslatableType.GATHERING, entityId, Map.of("title", ko));

        // then
        then(client).should(never()).translate(any(), any());
    }

    @Test
    @DisplayName("번역 호출 실패 → FAILED로 기록 (ko fallback)")
    void translate_clientFails_marksFailed() {
        // given
        given(client.isEnabled()).willReturn(true);
        given(translationRepository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(
                any(), any(), any(), any())).willReturn(Optional.empty());
        given(client.translate(any(), any())).willThrow(new RuntimeException("GMS down"));

        // when
        autoTranslationService.translate(TranslatableType.GATHERING, entityId, Map.of("title", "안녕하세요"));

        // then
        then(translationService).should(times(2)).upsert(eq(TranslatableType.GATHERING), eq(entityId), eq("title"),
                any(), isNull(), eq(TranslationStatus.FAILED), anyString(), eq(false));
    }

    private String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
