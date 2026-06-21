-- 다국어 콘텐츠 번역 저장 (KAN-266).
-- 원문(ko)은 각 엔티티 테이블에 그대로 두고, en/ja 번역을 (엔티티종류, 엔티티ID, 필드, 로케일) 단위로 저장한다.
-- status/source_hash/is_override는 AI 자동 번역 파이프라인(KAN-267)이 사용한다.
CREATE TABLE IF NOT EXISTS content_translations (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(40)  NOT NULL,
    entity_id    UUID         NOT NULL,
    field        VARCHAR(40)  NOT NULL,
    locale       VARCHAR(5)   NOT NULL,
    value        TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DONE',
    source_hash  VARCHAR(64),
    is_override  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    deleted_at   TIMESTAMP,
    CONSTRAINT uq_content_translation UNIQUE (entity_type, entity_id, field, locale)
);

CREATE INDEX IF NOT EXISTS idx_content_translation_lookup
    ON content_translations (entity_type, entity_id, locale);
