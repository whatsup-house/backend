package com.whatsuphouse.backend.domain.translation.repository;

import com.whatsuphouse.backend.domain.translation.entity.ContentTranslation;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, UUID> {

    // 단일 엔티티의 특정 로케일 번역 전체(필드별)
    List<ContentTranslation> findByEntityTypeAndEntityIdAndLocaleAndDeletedAtIsNull(
            TranslatableType entityType, UUID entityId, String locale);

    // 목록 조회용: 여러 엔티티의 특정 로케일 번역 일괄 (N+1 방지)
    List<ContentTranslation> findByEntityTypeAndEntityIdInAndLocaleAndDeletedAtIsNull(
            TranslatableType entityType, Collection<UUID> entityIds, String locale);

    // upsert/조회용: 특정 필드 1건
    Optional<ContentTranslation> findByEntityTypeAndEntityIdAndFieldAndLocaleAndDeletedAtIsNull(
            TranslatableType entityType, UUID entityId, String field, String locale);
}
