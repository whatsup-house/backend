-- 2026년 7~8월 정기 게더링 일괄 생성
--   목요일 = 우연한 식탁 (RANDOM_TABLE)
--   금요일 = 퇴근 게더링 (REGULAR)
--
-- 내용을 손으로 옮겨 적지 않고, 운영 DB에 이미 있는 게더링 행을 통째로 복사한다.
-- (제목·소개·진행방식·태그·장소·시간·참가비·정원·썸네일 + 신청폼 13문항과 선택지까지)
--
-- 복사 원본
--   우연한 식탁  3bf79fac-9f62-523d-92d2-68ceea17c41f  (2026-08-06)
--   퇴근 게더링  9afb8c25-cea0-5eec-acd0-f7fbf8b133c9  (2026-07-10)
--
-- 생성 대상 13건. 아래 5건은 이미 있어서 제외했다.
--   7/02·7/09·7/16 우연한 식탁, 7/10 퇴근 게더링, 8/06 우연한 식탁
--
-- 상태는 날짜로 결정한다. 실행 시점 기준 지난 날짜는 COMPLETED, 그 외는 OPEN.
-- created_at도 지난 건은 행사일로 잡아 목록 정렬이 어색해지지 않게 한다.
--
-- 재실행해도 안전하다. 새 id를 (원본id + 날짜) 해시로 고정하고 ON CONFLICT DO NOTHING을 쓴다.
--
-- 실행:
--   psql "$DB_URL" -v ON_ERROR_STOP=1 -f 2026-07-08-recurring-gatherings.sql

BEGIN;

-- 1. 생성 대상: 새 게더링 id, 날짜, 복사 원본 id
CREATE TEMP TABLE seed_targets ON COMMIT DROP AS
SELECT
    (
        SUBSTR(MD5('recurring-' || v.template_id::text || '-' || v.new_date::text), 1, 8) || '-' ||
        SUBSTR(MD5('recurring-' || v.template_id::text || '-' || v.new_date::text), 9, 4) || '-' ||
        SUBSTR(MD5('recurring-' || v.template_id::text || '-' || v.new_date::text), 13, 4) || '-' ||
        SUBSTR(MD5('recurring-' || v.template_id::text || '-' || v.new_date::text), 17, 4) || '-' ||
        SUBSTR(MD5('recurring-' || v.template_id::text || '-' || v.new_date::text), 21, 12)
    )::uuid AS new_id,
    v.new_date,
    v.template_id
FROM (VALUES
    -- 목요일 · 우연한 식탁   (7/02·7/09·7/16·8/06은 이미 존재)
    (DATE '2026-07-23', UUID '3bf79fac-9f62-523d-92d2-68ceea17c41f'),
    (DATE '2026-07-30', UUID '3bf79fac-9f62-523d-92d2-68ceea17c41f'),
    (DATE '2026-08-13', UUID '3bf79fac-9f62-523d-92d2-68ceea17c41f'),
    (DATE '2026-08-20', UUID '3bf79fac-9f62-523d-92d2-68ceea17c41f'),
    (DATE '2026-08-27', UUID '3bf79fac-9f62-523d-92d2-68ceea17c41f'),
    -- 금요일 · 퇴근 게더링   (7/10은 이미 존재)
    (DATE '2026-07-03', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-07-17', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-07-24', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-07-31', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-08-07', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-08-14', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-08-21', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9'),
    (DATE '2026-08-28', UUID '9afb8c25-cea0-5eec-acd0-f7fbf8b133c9')
) AS v(new_date, template_id);

-- 안전장치 1: 복사 원본이 실제로 있어야 한다.
DO $$
DECLARE missing int;
BEGIN
    SELECT COUNT(*) INTO missing
    FROM (SELECT DISTINCT template_id FROM seed_targets) t
    WHERE NOT EXISTS (
        SELECT 1 FROM gatherings g WHERE g.id = t.template_id AND g.deleted_at IS NULL
    );
    IF missing > 0 THEN
        RAISE EXCEPTION '복사 원본 게더링을 찾을 수 없습니다 (missing=%)', missing;
    END IF;
END $$;

-- 안전장치 2: 같은 날짜·같은 제목이 이미 있으면 중복 생성하지 않도록 대상에서 뺀다.
DELETE FROM seed_targets t
USING gatherings g, gatherings src
WHERE src.id = t.template_id
  AND g.event_date = t.new_date
  AND g.title = src.title
  AND g.deleted_at IS NULL
  AND g.id <> t.new_id;

-- 2. 게더링 복사. 날짜·상태·큐레이션만 새로 지정한다.
INSERT INTO gatherings (
    id, title, description, how_to_run, tags, location_id, event_date, start_time, end_time,
    price, max_attendees, gathering_type, status, thumbnail_url, is_curated, curated_rank,
    created_at, updated_at
)
SELECT
    t.new_id,
    g.title, g.description, g.how_to_run, g.tags, g.location_id,
    t.new_date, g.start_time, g.end_time,
    g.price, g.max_attendees, g.gathering_type,
    CASE WHEN t.new_date < CURRENT_DATE THEN 'COMPLETED' ELSE 'OPEN' END,
    g.thumbnail_url,
    FALSE, 0,                                   -- 홈 큐레이션은 기존 운영 그대로 두고 건드리지 않는다
    LEAST(NOW(), t.new_date::timestamp),
    LEAST(NOW(), t.new_date::timestamp)
FROM seed_targets t
JOIN gatherings g ON g.id = t.template_id
ON CONFLICT (id) DO NOTHING;

-- 3. 신청폼 생성. 폼 id도 게더링 id에서 결정적으로 파생시킨다.
INSERT INTO forms (id, gathering_id, is_template, gathering_type, guide_text, created_at, updated_at)
SELECT
    (
        SUBSTR(MD5('recurring-form-' || t.new_id::text), 1, 8) || '-' ||
        SUBSTR(MD5('recurring-form-' || t.new_id::text), 9, 4) || '-' ||
        SUBSTR(MD5('recurring-form-' || t.new_id::text), 13, 4) || '-' ||
        SUBSTR(MD5('recurring-form-' || t.new_id::text), 17, 4) || '-' ||
        SUBSTR(MD5('recurring-form-' || t.new_id::text), 21, 12)
    )::uuid,
    t.new_id,
    FALSE,
    src_form.gathering_type,
    src_form.guide_text,
    LEAST(NOW(), t.new_date::timestamp),
    LEAST(NOW(), t.new_date::timestamp)
FROM seed_targets t
JOIN forms src_form
  ON src_form.gathering_id = t.template_id
 AND src_form.deleted_at IS NULL
WHERE EXISTS (SELECT 1 FROM gatherings g WHERE g.id = t.new_id)
ON CONFLICT (id) DO NOTHING;

-- 4. 폼 문항 복사 (선택지 options·검증 validation·매칭 설정 포함).
--    질문이 이미 들어간 폼은 건너뛴다.
INSERT INTO form_questions (
    form_id, question_key, type, label, placeholder, required, display_order,
    options, validation, is_matching_field, is_system_reserved,
    matching_strategy, matching_weight, created_at, updated_at
)
SELECT
    new_form.id,
    q.question_key, q.type, q.label, q.placeholder, q.required, q.display_order,
    q.options, q.validation, q.is_matching_field, q.is_system_reserved,
    q.matching_strategy, q.matching_weight,
    LEAST(NOW(), t.new_date::timestamp),
    LEAST(NOW(), t.new_date::timestamp)
FROM seed_targets t
JOIN forms new_form
  ON new_form.gathering_id = t.new_id
 AND new_form.deleted_at IS NULL
JOIN forms src_form
  ON src_form.gathering_id = t.template_id
 AND src_form.deleted_at IS NULL
JOIN form_questions q
  ON q.form_id = src_form.id
 AND q.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM form_questions existing
    WHERE existing.form_id = new_form.id AND existing.deleted_at IS NULL
);

-- 5. 결과 확인 — 13행이 나오고 question_count가 모두 같아야 정상이다.
SELECT g.event_date,
       TO_CHAR(g.event_date, 'Dy') AS dow,
       g.title,
       g.status,
       (SELECT COUNT(*) FROM form_questions q
          JOIN forms f ON f.id = q.form_id
         WHERE f.gathering_id = g.id AND q.deleted_at IS NULL) AS question_count
FROM gatherings g
JOIN seed_targets t ON t.new_id = g.id
ORDER BY g.event_date;

COMMIT;
