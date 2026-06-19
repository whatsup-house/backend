package com.whatsuphouse.backend.domain.translation.entity;

import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.enums.TranslationStatus;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 콘텐츠 번역 저장. (엔티티종류, 엔티티ID, 필드, 로케일) 단위로 en/ja 번역을 담는다. (KAN-266)
 * 원문(ko)은 각 엔티티에 그대로 두고, 여기엔 번역본만 저장한다.
 */
@Entity
@Table(name = "content_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTranslation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private TranslatableType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 40)
    private String field;

    @Column(nullable = false, length = 5)
    private String locale;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranslationStatus status;

    // 번역 시점의 ko 원문 해시. 원문이 바뀌었을 때만 재번역하기 위한 캐싱 기준. (KAN-267)
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    // 관리자가 직접 수정한 번역은 자동 재번역으로 덮어쓰지 않는다. (KAN-267)
    @Column(name = "is_override", nullable = false)
    private boolean isOverride = false;

    @Builder
    public ContentTranslation(TranslatableType entityType, UUID entityId, String field, String locale,
                              String value, TranslationStatus status, String sourceHash, boolean isOverride) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.field = field;
        this.locale = locale;
        this.value = value;
        this.status = status;
        this.sourceHash = sourceHash;
        this.isOverride = isOverride;
    }

    public void update(String value, TranslationStatus status, String sourceHash, boolean isOverride) {
        this.value = value;
        this.status = status;
        this.sourceHash = sourceHash;
        this.isOverride = isOverride;
    }
}
