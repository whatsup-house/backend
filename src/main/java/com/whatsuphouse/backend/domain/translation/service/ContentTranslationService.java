package com.whatsuphouse.backend.domain.translation.service;

import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import com.whatsuphouse.backend.domain.translation.repository.ContentTranslationRepository;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 콘텐츠 번역 조회/저장. 조회는 요청 로케일로 번역을 적용하고 누락 시 ko 원문으로 폴백한다. (KAN-266)
 * 저장(upsert)은 AI 자동 번역 파이프라인(KAN-267)이, 조회/보정(getTranslations/override)은 관리자 API(KAN-274)가 사용한다.
 */
@Service
@RequiredArgsConstructor
public class ContentTranslationService {

    private final ContentTranslationRepository repository;

    // 단일 엔티티의 번역을 한 번에 읽어, 필드별 ko 폴백을 적용할 수 있는 Localizer를 만든다. (N+1 방지)
    @Transactional(readOnly = true)
    public Localizer localizer(TranslatableType type, UUID entityId, AppLocale locale) {
        if (locale == AppLocale.KO) {
            return new Localizer(Map.of());
        }
        Map<String, String> byField = repository
                .findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(type, entityId, locale.getCode())
                .stream()
                .filter(translation -> translation.getStatus() == TranslationStatus.DONE)
                .filter(translation -> translation.getValue() != null && !translation.getValue().isBlank())
                .collect(Collectors.toMap(ContentTranslation::getField, ContentTranslation::getValue, (a, b) -> a));
        return new Localizer(byField);
    }

    // 번역 결과를 저장/갱신한다. (KAN-266 제공, KAN-267 AI 파이프라인이 사용)
    @Transactional
    public void upsert(TranslatableType type, UUID entityId, String field, AppLocale locale,
                       String value, TranslationStatus status, String sourceHash, boolean isOverride) {
        repository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(type, entityId, field, locale.getCode())
                .ifPresentOrElse(
                        existing -> existing.update(value, status, sourceHash, isOverride),
                        () -> repository.save(ContentTranslation.builder()
                                .entityType(type)
                                .entityId(entityId)
                                .field(field)
                                .locale(locale.getCode())
                                .value(value)
                                .status(status)
                                .sourceHash(sourceHash)
                                .isOverride(isOverride)
                                .build())
                );
    }

    // 관리자: 엔티티의 모든 번역(로케일·필드·상태) 조회. (KAN-274)
    @Transactional(readOnly = true)
    public List<ContentTranslation> getTranslations(TranslatableType type, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdAndDeletedAtIsNull(type, entityId);
    }

    // 관리자: 번역 수동 보정. isOverride=true, status=DONE으로 저장해 자동 재번역이 덮어쓰지 않게 한다. (KAN-274)
    @Transactional
    public ContentTranslation override(TranslatableType type, UUID entityId, String field, AppLocale locale, String value) {
        return repository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(type, entityId, field, locale.getCode())
                .map(existing -> {
                    existing.update(value, TranslationStatus.DONE, existing.getSourceHash(), true);
                    return existing;
                })
                .orElseGet(() -> repository.save(ContentTranslation.builder()
                        .entityType(type)
                        .entityId(entityId)
                        .field(field)
                        .locale(locale.getCode())
                        .value(value)
                        .status(TranslationStatus.DONE)
                        .sourceHash(null)
                        .isOverride(true)
                        .build()));
    }

    // 미리 로드된 번역 맵으로 필드별 ko 폴백을 적용한다.
    public static class Localizer {
        private final Map<String, String> byField;

        Localizer(Map<String, String> byField) {
            this.byField = byField;
        }

        public String get(String field, String koValue) {
            String translated = byField.get(field);
            return (translated != null && !translated.isBlank()) ? translated : koValue;
        }
    }
}
