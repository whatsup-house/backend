-- V10: 자동 확정 파이프라인 — 참석(attendances) + 알림 이동 대상 ID (KAN-346)
-- 설계: docs/superpowers/specs/2026-09-29-dining-table-v2-design.md 2.6, 2.8, 4.6
--
-- - 테이블이 확정(CONFIRMED)될 때 멤버마다 SCHEDULED 1행. 체크인·노쇼 처리는 KAN-349.
-- - notifications.link_id: link가 가리키는 대상 ID(DINING_TABLE이면 dining_tables.id). 기존 링크는 NULL.

CREATE TABLE IF NOT EXISTS attendances (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    table_member_id   UUID        NOT NULL REFERENCES dining_table_members(id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',   -- SCHEDULED|ATTENDED|CANCELED_EARLY|CANCELED_LATE|NO_SHOW
    checked_in_at     TIMESTAMP,
    no_show_candidate BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by        UUID        REFERENCES users(id),
    created_at        TIMESTAMP   NOT NULL,
    updated_at        TIMESTAMP   NOT NULL,
    CONSTRAINT uk_attendances_table_member UNIQUE (table_member_id)
);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link_id UUID;
