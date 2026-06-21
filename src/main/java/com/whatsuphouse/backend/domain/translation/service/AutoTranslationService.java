package com.whatsuphouse.backend.domain.translation.service;

import com.whatsuphouse.backend.domain.translation.client.GmsTranslationClient;
import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import com.whatsuphouse.backend.domain.translation.repository.ContentTranslationRepository;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 관리자 콘텐츠(ko)를 en/ja로 자동 번역해 저장한다. (KAN-267)
 * 쓰기 시점 비동기 1회 번역 + 원문 해시 캐싱(변경분만) + override(수동수정) 보존 + 실패 시 상태 기록.
 * 키 미설정/비활성 시 no-op(조회는 ko 폴백).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTranslationService {

    private static final List<AppLocale> TARGET_LOCALES = List.of(AppLocale.EN, AppLocale.JA);

    private final GmsTranslationClient gmsTranslationClient;
    private final ContentTranslationRepository contentTranslationRepository;
    private final ContentTranslationService contentTranslationService;

    @Async("translationTaskExecutor")
    public void translateEntity(TranslatableType type, UUID entityId, Map<String, String> koFields) {
        if (!gmsTranslationClient.isEnabled()) {
            log.info("[AutoTranslation] 비활성(GMS 키 없음) — {} {} 스킵", type, entityId);
            return;
        }
        koFields.forEach((field, koValue) -> {
            if (!StringUtils.hasText(koValue)) {
                return;
            }
            String hash = sha256(koValue);
            for (AppLocale locale : TARGET_LOCALES) {
                translateField(type, entityId, field, koValue, hash, locale);
            }
        });
    }

    private void translateField(TranslatableType type, UUID entityId, String field, String koValue,
                                String hash, AppLocale locale) {
        Optional<ContentTranslation> existing = contentTranslationRepository
                .findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(type, entityId, field, locale.getCode());

        if (existing.isPresent()) {
            ContentTranslation current = existing.get();
            if (current.isOverride()) {
                return; // 관리자가 직접 수정한 번역은 보존(덮어쓰지 않음)
            }
            if (current.getStatus() == TranslationStatus.DONE && hash.equals(current.getSourceHash())) {
                return; // 원문 변경 없음 → 재번역 안 함(토큰 절약)
            }
        }

        try {
            String translated = gmsTranslationClient.translate(koValue, locale);
            if (StringUtils.hasText(translated)) {
                contentTranslationService.upsert(type, entityId, field, locale, translated, TranslationStatus.DONE, hash, false);
            } else {
                contentTranslationService.upsert(type, entityId, field, locale, null, TranslationStatus.FAILED, hash, false);
            }
        } catch (Exception e) {
            log.warn("[AutoTranslation] 번역 실패 {} {} {} {} : {}", type, entityId, field, locale, e.getMessage());
            contentTranslationService.upsert(type, entityId, field, locale, null, TranslationStatus.FAILED, hash, false);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
