-- V5: 모임 종류(gatherings) / 회차(gathering_sessions) 분리 (KAN-337)
--
-- 배경: 같은 제목의 모임을 하나의 종류로 묶고, 날짜·시간·장소·정원·가격·상태는 회차로 옮긴다.
-- 기존 gatherings 각 행은 같은 id로 회차가 된다(옛 게더링 ID = 회차 ID). 그래서 applications,
-- matching_groups, chat_rooms(source_type=GATHERING)의 옛 게더링 참조 값은 그대로 회차 참조가 된다.
-- gatherings에서 회차로 옮긴 컬럼(event_date, start_time, end_time, location_id, price, max_attendees,
-- status)은 롤백 여지를 위해 이번 릴리스에서 drop 하지 않고 nullable로만 바꾼다. (다음 릴리스에서 제거)
--
-- [주의] 제목(+타입)이 같은 모임은 하나의 종류로 합쳐진다. 제목만 같고 실제로 다른 모임이 있으면
-- 실행 전에 제목을 바꿔 두어야 한다. 실행 전 확인 쿼리(운영자 확인 필수):
--
--   SELECT title,
--          COALESCE(gathering_type, 'REGULAR')                   AS gathering_type,
--          count(*)                                              AS row_count,
--          array_agg(id ORDER BY created_at)                     AS ids,
--          array_agg(DISTINCT description)                       AS descriptions,
--          array_agg(DISTINCT price)                             AS prices,
--          array_agg(DISTINCT thumbnail_url)                     AS thumbnails,
--          array_agg(is_curated ORDER BY created_at)             AS curated_flags,
--          array_agg(curated_rank ORDER BY created_at)           AS curated_ranks
--     FROM gatherings
--    GROUP BY title, COALESCE(gathering_type, 'REGULAR')
--   HAVING count(*) > 1
--    ORDER BY row_count DESC, title;
--
-- 합치는 규칙
--   - 그룹 키: title + gathering_type(NULL은 REGULAR로 본다). 타입이 다르면 제목이 같아도 합치지 않는다.
--   - 대표 행(종류로 남는 행): 삭제되지 않은 행 중 created_at이 가장 이른 행(동률이면 id 순).
--     종류의 소개·썸네일·폼·base_price는 대표 행 값을 쓴다.
--   - 큐레이션은 그룹 승계: 삭제되지 않은 행 중 하나라도 큐레이션이면 종류도 큐레이션,
--     순위는 그 행들 중 가장 앞 순위(MIN(curated_rank)). 대표 행만 보면 비대표 행의 노출 설정이 사라진다.
--   - 나머지 행은 회차로만 남고 gatherings에서 삭제된다.

-- ================================================
-- 1. 회차 테이블 + 기존 게더링 행 복사 (같은 id)
-- ================================================
CREATE TABLE IF NOT EXISTS gathering_sessions (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    gathering_id               UUID         NOT NULL REFERENCES gatherings(id),
    event_date                 DATE         NOT NULL,
    start_time                 TIME,
    end_time                   TIME,
    location_id                UUID         REFERENCES locations(id),
    max_attendees              INTEGER      NOT NULL,
    price_override             INTEGER,
    apply_deadline_at          TIMESTAMP,
    status                     VARCHAR(20)  NOT NULL,
    -- RANDOM_TABLE 전용, 그 외 NULL. NULL이면 매칭 규칙 기본값을 쓴다.
    match_run_at               TIMESTAMP,
    auto_confirm_grace_minutes INTEGER,
    table_size_min             INTEGER      DEFAULT 4,
    table_size_max             INTEGER      DEFAULT 6,
    min_group_score            NUMERIC(5,4),
    max_age_gap                INTEGER,
    created_at                 TIMESTAMP    NOT NULL,
    updated_at                 TIMESTAMP    NOT NULL,
    deleted_at                 TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gathering_sessions_gathering_id ON gathering_sessions(gathering_id);
CREATE INDEX IF NOT EXISTS idx_gathering_sessions_event_date ON gathering_sessions(event_date);

-- gathering_id = 그룹 대표 행. 이후 단계는 이 테이블(id = 옛 게더링 ID, gathering_id = 대표 행 ID)을 매핑표로 쓴다.
INSERT INTO gathering_sessions (id, gathering_id, event_date, start_time, end_time, location_id, max_attendees,
                                price_override, status, table_size_min, table_size_max,
                                created_at, updated_at, deleted_at)
SELECT g.id,
       FIRST_VALUE(g.id) OVER (
           PARTITION BY g.title, COALESCE(g.gathering_type, 'REGULAR')
           ORDER BY (g.deleted_at IS NOT NULL), g.created_at, g.id),
       g.event_date,
       g.start_time,
       g.end_time,
       g.location_id,
       g.max_attendees,
       g.price,
       CASE g.status WHEN 'COMPLETED' THEN 'DONE' ELSE g.status END,
       CASE WHEN g.gathering_type = 'RANDOM_TABLE' THEN 4 END,
       CASE WHEN g.gathering_type = 'RANDOM_TABLE' THEN 6 END,
       g.created_at,
       g.updated_at,
       g.deleted_at
  FROM gatherings g;

-- ================================================
-- 2. gatherings: 기본 가격 컬럼 추가, 회차로 옮긴 컬럼은 nullable로 (drop은 다음 릴리스)
-- ================================================
ALTER TABLE gatherings ADD COLUMN IF NOT EXISTS base_price INTEGER;
UPDATE gatherings SET base_price = price;

-- 큐레이션 승계: 그룹 내 삭제되지 않은 큐레이션 행이 있으면 대표 행(종류)에 노출 여부·가장 앞 순위를 옮긴다.
UPDATE gatherings g
   SET is_curated   = TRUE,
       curated_rank = agg.min_rank
  FROM (SELECT s.gathering_id AS rep_id, MIN(src.curated_rank) AS min_rank
          FROM gathering_sessions s
          JOIN gatherings src ON src.id = s.id
         WHERE src.is_curated
           AND src.deleted_at IS NULL
         GROUP BY s.gathering_id) agg
 WHERE g.id = agg.rep_id;

ALTER TABLE gatherings ALTER COLUMN event_date DROP NOT NULL;
ALTER TABLE gatherings ALTER COLUMN max_attendees DROP NOT NULL;
ALTER TABLE gatherings ALTER COLUMN status DROP NOT NULL;

-- ================================================
-- 3. applications: 회차·매칭 상태 컬럼, 희망 회차 테이블
-- ================================================
ALTER TABLE applications ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES gathering_sessions(id);
ALTER TABLE applications ADD COLUMN IF NOT EXISTS match_status VARCHAR(20);

-- 옛 gathering_id가 곧 회차 ID. gathering_id는 대표 행(종류)으로 바꾼다.
UPDATE applications SET session_id = gathering_id WHERE session_id IS NULL;

UPDATE applications a
   SET gathering_id = s.gathering_id
  FROM gathering_sessions s
 WHERE s.id = a.session_id
   AND a.gathering_id <> s.gathering_id;

CREATE INDEX IF NOT EXISTS idx_applications_session_id ON applications(session_id);

CREATE TABLE IF NOT EXISTS application_candidate_sessions (
    id             UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID      NOT NULL REFERENCES applications(id),
    session_id     UUID      NOT NULL REFERENCES gathering_sessions(id),
    priority       INTEGER   NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_application_candidate_sessions UNIQUE (application_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_application_candidate_sessions_session_id
    ON application_candidate_sessions(session_id);

-- 기존 신청은 회차 하나만 골랐으므로 1순위 1행씩.
INSERT INTO application_candidate_sessions (application_id, session_id, priority, created_at)
SELECT a.id, a.session_id, 1, a.created_at
  FROM applications a;

-- ================================================
-- 4. matching_groups: gathering_id → session_id (값은 그대로 = 회차 ID)
-- ================================================
-- FK 이름이 환경마다 다를 수 있어(Flyway 도입 전 Hibernate 생성분) gatherings를 가리키는 FK를 조회해 지운다.
DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'matching_groups'::regclass
           AND confrelid = 'gatherings'::regclass
           AND contype = 'f'
    LOOP
        EXECUTE format('ALTER TABLE matching_groups DROP CONSTRAINT %I', fk.conname);
    END LOOP;
END $$;

ALTER TABLE matching_groups RENAME COLUMN gathering_id TO session_id;
ALTER TABLE matching_groups
    ADD CONSTRAINT fk_matching_groups_session_id FOREIGN KEY (session_id) REFERENCES gathering_sessions(id);
ALTER INDEX IF EXISTS idx_matching_groups_gathering_id RENAME TO idx_matching_groups_session_id;

-- ================================================
-- 5. 종류 단위 참조(후기·캐러셀·폼)를 대표 행으로
-- ================================================
UPDATE reviews r
   SET gathering_id = s.gathering_id
  FROM gathering_sessions s
 WHERE s.id = r.gathering_id
   AND r.gathering_id <> s.gathering_id;

UPDATE carousel_slides c
   SET gathering_id = s.gathering_id
  FROM gathering_sessions s
 WHERE s.id = c.gathering_id
   AND c.gathering_id <> s.gathering_id;

-- 폼은 종류당 하나만 활성으로 남긴다: 대표 행의 폼 우선, 없으면 그룹에서 가장 먼저 만든 게더링의 폼.
-- 나머지 폼은 soft delete 한다(기존 신청 답변이 그 폼의 질문을 참조하므로 행은 지우지 않는다).
UPDATE forms f
   SET deleted_at = now(),
       updated_at = now()
  FROM (SELECT f2.id,
               ROW_NUMBER() OVER (
                   PARTITION BY s.gathering_id
                   ORDER BY (f2.gathering_id = s.gathering_id) DESC, s.created_at, f2.created_at, f2.id) AS rn
          FROM forms f2
          JOIN gathering_sessions s ON s.id = f2.gathering_id
         WHERE f2.deleted_at IS NULL) ranked
 WHERE f.id = ranked.id
   AND ranked.rn > 1;

UPDATE forms f
   SET gathering_id = s.gathering_id
  FROM gathering_sessions s
 WHERE s.id = f.gathering_id
   AND f.gathering_id <> s.gathering_id;

-- ================================================
-- 6. 대표 행이 아닌 게더링 행 삭제 (회차로만 남는다)
-- ================================================
-- 삭제되는 종류의 번역(title/description)은 대표 행 번역으로 대체되므로 함께 지운다.
DELETE FROM content_translations t
 USING gathering_sessions s
 WHERE t.entity_type = 'GATHERING'
   AND t.entity_id = s.id
   AND s.gathering_id <> s.id;

DELETE FROM gatherings g
 USING gathering_sessions s
 WHERE s.id = g.id
   AND s.gathering_id <> g.id;
