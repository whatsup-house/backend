package com.whatsuphouse.backend.domain.translation.service;

import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import com.whatsuphouse.backend.domain.translation.repository.ContentTranslationRepository;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import com.whatsuphouse.backend.global.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 번역 조회 캐시(서빙 레이어, KAN-280) 동작 검증.
 * 캐시 어노테이션은 Spring 프록시가 필요하므로 캐시 인프라(CacheConfig)와 서비스만 띄운 슬라이스 컨텍스트로 검증한다.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, ContentTranslationService.class})
class ContentTranslationServiceCacheTest {

    @MockitoBean
    private ContentTranslationRepository repository;

    @Autowired
    private ContentTranslationService contentTranslationService;

    @Autowired
    private CacheManager cacheManager;

    private UUID entityId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        cacheManager.getCache(CacheConfig.CONTENT_TRANSLATION_CACHE).clear();
    }

    private ContentTranslation doneTranslation(String field, String value) {
        return ContentTranslation.builder()
                .entityType(TranslatableType.GATHERING)
                .entityId(entityId)
                .field(field)
                .locale(AppLocale.EN.getCode())
                .value(value)
                .status(TranslationStatus.DONE)
                .sourceHash("hash")
                .isOverride(false)
                .build();
    }

    @Test
    @DisplayName("같은 (type, id, locale) 재조회 → DB는 1회만 조회 (캐시 히트)")
    void localizer_sameKey_hitsRepositoryOnce() {
        // given
        given(repository.findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(
                TranslatableType.GATHERING, entityId, AppLocale.EN.getCode()))
                .willReturn(List.of(doneTranslation("title", "Dinner")));

        // when
        ContentTranslationService.Localizer first =
                contentTranslationService.localizer(TranslatableType.GATHERING, entityId, AppLocale.EN);
        ContentTranslationService.Localizer second =
                contentTranslationService.localizer(TranslatableType.GATHERING, entityId, AppLocale.EN);

        // then
        assertThat(first.get("title", "제목")).isEqualTo("Dinner");
        assertThat(second.get("title", "제목")).isEqualTo("Dinner");
        verify(repository, times(1)).findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(
                TranslatableType.GATHERING, entityId, AppLocale.EN.getCode());
    }

    @Test
    @DisplayName("KO 조회 → DB 미조회 + 캐시에 적재되지 않음")
    void localizer_ko_notCached() {
        // when
        contentTranslationService.localizer(TranslatableType.GATHERING, entityId, AppLocale.KO);
        contentTranslationService.localizer(TranslatableType.GATHERING, entityId, AppLocale.KO);

        // then
        verify(repository, never()).findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(any(), any(), any());
        String koKey = TranslatableType.GATHERING + ":" + entityId + ":" + AppLocale.KO;
        assertThat(cacheManager.getCache(CacheConfig.CONTENT_TRANSLATION_CACHE).get(koKey)).isNull();
    }

    @Test
    @DisplayName("upsert 후 재조회 → 캐시 무효화되어 DB 재조회")
    void upsert_evictsCache_reQueriesRepository() {
        // given
        given(repository.findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(
                TranslatableType.GATHERING, entityId, AppLocale.EN.getCode()))
                .willReturn(List.of(doneTranslation("title", "Dinner")));
        given(repository.findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(
                TranslatableType.GATHERING, entityId, "title", AppLocale.EN.getCode()))
                .willReturn(Optional.empty());

        // when
        contentTranslationService.localizer(TranslatableType.GATHERING, entityId, AppLocale.EN); // 캐시 적재
        contentTranslationService.upsert(TranslatableType.GATHERING, entityId, "title", AppLocale.EN,
                "Supper", TranslationStatus.DONE, "hash2", false); // 캐시 무효화
        contentTranslationService.localizer(TranslatableType.GATHERING, entityId, AppLocale.EN); // 재조회

        // then
        verify(repository, times(2)).findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(
                TranslatableType.GATHERING, entityId, AppLocale.EN.getCode());
    }
}
