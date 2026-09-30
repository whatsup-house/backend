-- V7: 우연한 식탁 운영자 어드민 — 예외함·매칭 규칙 설정·식당 풀·회차별 식당 수용 (KAN-348)
-- 설계: docs/superpowers/specs/2026-09-29-dining-table-v2-design.md 2.4(exception_cases), 2.5, 2.7
--
-- 매칭 엔진 v2(KAN-345) 전이라 dining_tables가 아직 없다. 그래서
--   - exception_cases.session_id/table_id/application_id는 FK 없이 둔다. table_id는 지금은 matching_groups.id,
--     KAN-345 이후 dining_tables.id를 가리킨다.
--   - 테이블별 식당은 matching_groups.venue_id로 둔다(기존 restaurant_name/address는 그대로). KAN-345의 개명에 함께 따라간다.

-- ================================================
-- 1. 예외함
-- ================================================
CREATE TABLE IF NOT EXISTS exception_cases (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(20)  NOT NULL,   -- PAYMENT|DATA|VENUE|NOTIFICATION|SAFETY|CONFLICT|REFUND
    session_id      UUID,
    table_id        UUID,
    application_id  UUID,
    reason          TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN',   -- OPEN|RESOLVED
    action          VARCHAR(10),             -- SAFETY 처리 조치: WARN|RESTRICT|BAN
    resolved_by     UUID         REFERENCES users(id),
    resolution_note TEXT,
    created_at      TIMESTAMP    NOT NULL,
    resolved_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exception_cases_status_type ON exception_cases(status, type);
CREATE INDEX IF NOT EXISTS idx_exception_cases_session_id ON exception_cases(session_id);

-- ================================================
-- 2. 매칭 규칙 설정 (단일 행, id = 1). 회차의 동명 컬럼이 NULL이 아니면 회차 값이 우선한다.
-- ================================================
CREATE TABLE IF NOT EXISTS matching_rule_settings (
    id                         INTEGER      PRIMARY KEY CHECK (id = 1),
    max_age_gap                INTEGER      NOT NULL DEFAULT 8,
    table_size_min             INTEGER      NOT NULL DEFAULT 4,
    table_size_max             INTEGER      NOT NULL DEFAULT 6,
    min_group_score            NUMERIC(5,4) NOT NULL DEFAULT 0.35,
    auto_confirm_grace_minutes INTEGER      NOT NULL DEFAULT 120,
    weights                    JSONB        NOT NULL
        DEFAULT '{"gender": 1.0, "mbti": 1.0, "interests": 1.0, "wantedStyle": 1.0, "custom": 1.0}'::jsonb
);

INSERT INTO matching_rule_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- ================================================
-- 3. 식당 풀 + 회차별 수용 테이블 수
-- ================================================
CREATE TABLE IF NOT EXISTS venues (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    map_url     VARCHAR(500),
    price_range VARCHAR(50),
    region      VARCHAR(50)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    deleted_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_venues_region ON venues(region);

CREATE TABLE IF NOT EXISTS session_venues (
    session_id      UUID    NOT NULL REFERENCES gathering_sessions(id),
    venue_id        UUID    NOT NULL REFERENCES venues(id),
    capacity_tables INTEGER NOT NULL,
    used_tables     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id, venue_id),
    CONSTRAINT ck_session_venues_used_tables CHECK (used_tables >= 0 AND used_tables <= capacity_tables)
);

-- ================================================
-- 4. 테이블별 배정 식당
-- ================================================
ALTER TABLE matching_groups ADD COLUMN IF NOT EXISTS venue_id UUID REFERENCES venues(id);
