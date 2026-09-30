-- V12: 우연한 식탁 피드백·사람별 선호·신고 (KAN-350)
-- 설계: docs/superpowers/specs/2026-09-29-dining-table-v2-design.md 2.6, 4.3
--
-- - V9(dining_tables·dining_table_members)와 V7(exception_cases)만 참조한다. V10·V11과 독립적이다.
-- - peer_preferences·safety_reports는 운영자·매칭 엔진 전용(참가자 응답에 노출하지 않는다).
--   매칭 하드 조건: AVOID(단방향이라도)·신고(양방향) 쌍은 같은 테이블 금지, AGAIN 쌍은 이전 만남 페널티 면제.

-- ================================================
-- 1. 테이블 피드백 (멤버당 1회)
-- ================================================
CREATE TABLE IF NOT EXISTS feedbacks (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    table_member_id UUID        NOT NULL UNIQUE REFERENCES dining_table_members(id),
    table_score     INTEGER     NOT NULL CHECK (table_score BETWEEN 1 AND 5),
    talk_score      INTEGER     NOT NULL CHECK (talk_score BETWEEN 1 AND 5),
    venue_score     INTEGER     NOT NULL CHECK (venue_score BETWEEN 1 AND 5),
    rejoin_intent   VARCHAR(10) NOT NULL CHECK (rejoin_intent IN ('YES', 'MAYBE', 'NO')),
    comment         TEXT,
    created_at      TIMESTAMP   NOT NULL
);

-- ================================================
-- 2. 사람별 선호 (피드백과 함께 저장)
-- ================================================
CREATE TABLE IF NOT EXISTS peer_preferences (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID        NOT NULL REFERENCES users(id),
    to_user_id   UUID        NOT NULL REFERENCES users(id),
    kind         VARCHAR(10) NOT NULL CHECK (kind IN ('AGAIN', 'AVOID')),
    table_id     UUID        NOT NULL REFERENCES dining_tables(id),
    created_at   TIMESTAMP   NOT NULL,
    CONSTRAINT uk_peer_preferences_from_to_table UNIQUE (from_user_id, to_user_id, table_id)
);

-- ================================================
-- 3. 안전 신고. 접수 시 exception_cases(SAFETY)를 함께 만들고, 그 예외 처리 결과(상태·조치·메모)를 따라간다.
-- ================================================
CREATE TABLE IF NOT EXISTS safety_reports (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id       UUID        NOT NULL REFERENCES users(id),
    reported_user_id  UUID        NOT NULL REFERENCES users(id),
    table_id          UUID        NOT NULL REFERENCES dining_tables(id),
    reason            TEXT        NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'OPEN',   -- OPEN|RESOLVED
    action            VARCHAR(10),                           -- WARN|RESTRICT|BAN, 조치 없이 처리하면 NULL
    admin_note        TEXT,
    exception_case_id UUID        REFERENCES exception_cases(id),
    created_at        TIMESTAMP   NOT NULL,
    resolved_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_safety_reports_reporter_id ON safety_reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_safety_reports_table_id ON safety_reports(table_id);
CREATE INDEX IF NOT EXISTS idx_safety_reports_exception_case_id ON safety_reports(exception_case_id);
