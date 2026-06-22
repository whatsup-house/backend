package com.whatsuphouse.backend.domain.translation.service;

import com.whatsuphouse.backend.domain.translation.client.GmsTranslationClient;
import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import com.whatsuphouse.backend.domain.translation.repository.ContentTranslationRepository;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ko 원문을 GMS로 en/ja 번역해 content_translations에 적재하는 자동 번역 파이프라인. (KAN-267)
 *
 * 동작 원칙:
 * - 쓰기 시점 1회 번역 후 저장 (조회 시 재번역 금지 → 토큰 절약). 호출은 비동기(translationExecutor)로 처리한다.
 * - 원문 해시(source_hash)로 변경분만 재번역하고, 동일 원문은 재번역하지 않는다.
 * - 관리자가 수동 보정한 번역(isOverride)은 자동 재번역으로 덮어쓰지 않는다.
 * - 토큰 예산 가드: 과도하게 긴 원문은 호출하지 않고 SKIPPED 기록 → ko fallback.
 * - 실패/예산초과/키 미설정은 모두 ko fallback으로 귀결된다(localizer가 DONE만 노출).
 */
@Service
@RequiredArgsConstructor
public class AutoTranslationService {

    private static final Logger log = LoggerFactory.getLogger(AutoTranslationService.class);
    private static final List<AppLocale> TARGET_LOCALES =
            List.of(AppLocale.EN, AppLocale.JA, AppLocale.ZH, AppLocale.ES);

    private final GmsTranslationClient client;
    private final ContentTranslationService translationService;
    private final ContentTranslationRepository translationRepository;

    @Value("${gms.max-input-chars:2000}")
    private int maxInputChars;

    @Async("translationExecutor")
    public void translate(TranslatableType type, UUID entityId, Map<String, String> koFields) {
        if (!client.isEnabled()) {
            log.info("[AutoTranslation] GMS 비활성(키 미설정/만료) → 자동 번역 스킵, ko fallback. type={}, id={}", type, entityId);
            return;
        }
        koFields.forEach((field, koText) -> {
            if (koText == null || koText.isBlank()) {
                return;
            }
            String sourceHash = sha256(koText);
            for (AppLocale target : TARGET_LOCALES) {
                translateField(type, entityId, field, target, koText, sourceHash);
            }
        });
    }

    private void translateField(TranslatableType type, UUID entityId, String field, AppLocale locale,
                                String koText, String sourceHash) {
        Optional<ContentTranslation> existing = translationRepository
                .findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(type, entityId, field, locale.getCode());
        if (existing.isPresent()) {
            ContentTranslation translation = existing.get();
            if (translation.isOverride()) {
                return; // 관리자 수동 보정 보존 (자동 재번역 금지)
            }
            if (sourceHash.equals(translation.getSourceHash()) && translation.getStatus() == TranslationStatus.DONE) {
                return; // 원문 미변경 + 이미 완료 → 재번역 금지 (토큰 절약)
            }
        }

        // 토큰 예산 가드: 호출 전 길이로 추정해 과도하면 스킵하고 ko fallback 한다.
        if (koText.length() > maxInputChars) {
            log.warn("[AutoTranslation] 입력 길이 초과로 스킵. type={}, id={}, field={}, locale={}, len={}",
                    type, entityId, field, locale, koText.length());
            translationService.upsert(type, entityId, field, locale, null, TranslationStatus.SKIPPED, sourceHash, false);
            return;
        }

        try {
            String translated = client.translate(koText, locale);
            if (translated == null) {
                translationService.upsert(type, entityId, field, locale, null, TranslationStatus.FAILED, sourceHash, false);
                return;
            }
            translationService.upsert(type, entityId, field, locale, translated, TranslationStatus.DONE, sourceHash, false);
        } catch (Exception e) {
            log.warn("[AutoTranslation] 번역 실패 → FAILED 기록(ko fallback). type={}, id={}, field={}, locale={}",
                    type, entityId, field, locale, e);
            translationService.upsert(type, entityId, field, locale, null, TranslationStatus.FAILED, sourceHash, false);
        }
    }

    // 번역 기준이 되는 ko 원문 해시. 원문이 바뀐 필드만 재번역하기 위한 비교 키.
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 표준 JDK에 항상 존재하므로 도달 불가.
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
