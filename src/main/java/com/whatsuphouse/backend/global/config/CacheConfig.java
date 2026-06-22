package com.whatsuphouse.backend.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 인프로세스 캐시(Caffeine) 설정. 번역 조회 서빙 레이어용. (KAN-280)
 *
 * contentTranslation 캐시: ContentTranslationService.localizer() 결과(Localizer)를
 * (entityType, entityId, locale) 단위로 캐싱해 매 요청 DB 조회를 제거한다.
 * - maximumSize=10_000     : 최대 엔트리 수 (초과 시 LRU 제거)
 * - expireAfterWrite=30분  : 안전망 TTL. 정상 무효화는 upsert()의 @CacheEvict가 담당하고,
 *                            upsert 미경유 직접 수정 등으로 인한 staleness 상한을 이 TTL이 보장한다.
 *
 * 1단계 인프로세스 캐시이므로 서버 다중화 시 인스턴스 간 캐시가 분리된다.
 * 다중화 단계에서는 Redis 기반 CacheManager로 교체한다. (후속)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CONTENT_TRANSLATION_CACHE = "contentTranslation";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CONTENT_TRANSLATION_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        return cacheManager;
    }
}
