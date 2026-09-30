-- V9: 매칭 엔진 v2 — matching_groups/matching_members 개명(dining_tables/dining_table_members) + match_runs (KAN-345)
-- 설계: docs/superpowers/specs/2026-09-29-dining-table-v2-design.md 2.4, 4장
--
-- - RENAME이라 기존 테이블·멤버 행과 ID가 그대로 남는다. chat_rooms(source_type=DINING_TABLE)·exception_cases.table_id가
--   가리키던 그룹 ID가 그대로 테이블 ID가 된다. 기존 컬럼(restaurant_name/address, venue_id, group_score, algorithm_version …)은 유지.
-- - 테이블 상태 값: PENDING → PROPOSED, CANCELLED → DISSOLVED (CONFIRMED는 그대로).
-- - dining_table_members.application_id 단독 UNIQUE를 없애고 UNIQUE(table_id, application_id)로 바꾼다.
--   해체된 테이블의 멤버 행은 이력으로 남기고, "활성(PROPOSED|CONFIRMED) 테이블에 한 신청은 1개만"은 서비스가 검사한다.
-- - V8(이용권 차감 상태, KAN-342)과 독립적이다.

-- ================================================
-- 1. 매칭 실행 기록
-- ================================================
CREATE TABLE IF NOT EXISTS match_runs (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id         UUID        NOT NULL REFERENCES gathering_sessions(id),
    triggered_by       VARCHAR(20) NOT NULL,   -- SCHEDULED|MANUAL
    triggered_user_id  UUID        REFERENCES users(id),
    started_at         TIMESTAMP   NOT NULL,
    finished_at        TIMESTAMP,
    candidate_count    INTEGER     NOT NULL DEFAULT 0,
    table_count        INTEGER     NOT NULL DEFAULT 0,
    split_count        INTEGER     NOT NULL DEFAULT 0,
    merge_count        INTEGER     NOT NULL DEFAULT 0,
    reallocated_count  INTEGER     NOT NULL DEFAULT 0,
    unassigned_count   INTEGER     NOT NULL DEFAULT 0,
    algorithm_version  VARCHAR(20) NOT NULL DEFAULT 'rule-v2',
    unassigned_reasons JSONB       NOT NULL DEFAULT '[]'::jsonb,   -- [{applicationId, reason}]
    CONSTRAINT uk_match_runs_session_started UNIQUE (session_id, started_at)
);

-- ================================================
-- 2. matching_groups → dining_tables
-- ================================================
ALTER TABLE matching_groups RENAME TO dining_tables;
ALTER INDEX IF EXISTS idx_matching_groups_session_id RENAME TO idx_dining_tables_session_id;

ALTER TABLE dining_tables
    ADD COLUMN match_run_id     UUID REFERENCES match_runs(id),
    ADD COLUMN score_detail     JSONB,                             -- {pairAvg, pairMin, penalties:{...}}
    ADD COLUMN confirm_at       TIMESTAMP,
    ADD COLUMN confirmed_at     TIMESTAMP,
    ADD COLUMN chat_room_id     UUID,
    ADD COLUMN reallocation_log JSONB,
    ADD COLUMN locked           BOOLEAN NOT NULL DEFAULT FALSE;

-- 상태 체계: PROPOSED|CONFIRMED|DONE|DISSOLVED
UPDATE dining_tables SET status = 'PROPOSED' WHERE status = 'PENDING';
UPDATE dining_tables SET status = 'DISSOLVED' WHERE status = 'CANCELLED';
UPDATE dining_tables SET confirmed_at = updated_at WHERE status = 'CONFIRMED' AND confirmed_at IS NULL;

-- ================================================
-- 3. matching_members → dining_table_members
-- ================================================
ALTER TABLE matching_members RENAME TO dining_table_members;
ALTER TABLE dining_table_members RENAME COLUMN group_id TO table_id;
ALTER TABLE dining_table_members RENAME COLUMN is_manual_assign TO is_manual;
ALTER INDEX IF EXISTS idx_matching_members_group_id RENAME TO idx_dining_table_members_table_id;

ALTER TABLE dining_table_members
    ADD COLUMN assign_reason VARCHAR(20) NOT NULL DEFAULT 'INITIAL';   -- INITIAL|REALLOCATED|MANUAL|SPLIT|MERGED
UPDATE dining_table_members SET assign_reason = 'MANUAL' WHERE is_manual;

-- application_id 단독 UNIQUE 제거. 이름이 환경마다 다를 수 있어(V1 인라인 UNIQUE, Flyway 도입 전 Hibernate 생성분) 조회해 지운다.
DO $$
DECLARE
    uk record;
BEGIN
    FOR uk IN
        SELECT c.conname
          FROM pg_constraint c
          JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
         WHERE c.conrelid = 'dining_table_members'::regclass
           AND c.contype = 'u'
           AND array_length(c.conkey, 1) = 1
           AND a.attname = 'application_id'
    LOOP
        EXECUTE format('ALTER TABLE dining_table_members DROP CONSTRAINT %I', uk.conname);
    END LOOP;
END $$;

ALTER TABLE dining_table_members
    ADD CONSTRAINT uk_dining_table_members_table_application UNIQUE (table_id, application_id);
CREATE INDEX IF NOT EXISTS idx_dining_table_members_application_id ON dining_table_members(application_id);
