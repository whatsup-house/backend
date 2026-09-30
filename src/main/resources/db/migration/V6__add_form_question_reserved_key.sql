-- V6: 우연한 식탁 표준 폼 — form_questions.reserved_key + '우연한 식탁 표준 폼' 시드 (KAN-341)
--
-- 1. form_questions.reserved_key 추가. 값은 ReservedQuestionKey enum 이름, 일반 질문은 NULL.
--    reserved_key가 있는 질문은 관리자 폼 API에서 삭제·질문 키/타입 변경이 막히고 라벨·선택지만 바뀐다.
-- 2. 템플릿 폼 '우연한 식탁 표준 폼'(is_template=TRUE, gathering_type=RANDOM_TABLE) + 표준 질문 7개.
--    새 RANDOM_TABLE 종류의 폼은 만들 때 이 템플릿의 표준 질문을 복사한다(FormProvisionService).
-- 3. 이미 있는 RANDOM_TABLE 종류 폼에도 표준 질문을 채운다(종류 폼은 표준 질문 7개를 모두 가져야 한다).
--    - question_key·타입이 같은 기존 질문(운영 폼의 gender/mbti/interests)은 새로 만들지 않고
--      reserved_key만 붙인다. 기존 답변이 그대로 표준 질문 답변이 된다.
--    - 나머지는 폼 맨 뒤에 추가한다.
-- 모든 단계는 reserved_key 기준으로 멱등하다(이미 있으면 다시 만들지 않는다).
--
-- 실행 후 확인(0행이어야 정상): 표준 질문이 7개가 아닌 RANDOM_TABLE 종류 폼
--   SELECT f.id, count(q.reserved_key) AS reserved_count
--     FROM forms f
--     JOIN gatherings g ON g.id = f.gathering_id AND g.gathering_type = 'RANDOM_TABLE' AND g.deleted_at IS NULL
--     LEFT JOIN form_questions q ON q.form_id = f.id AND q.deleted_at IS NULL AND q.reserved_key IS NOT NULL
--    WHERE f.deleted_at IS NULL
--    GROUP BY f.id
--   HAVING count(q.reserved_key) <> 7;
-- 같은 question_key인데 타입이 다른 기존 질문이 있으면 키 충돌을 피하려고 그 표준 질문은 건너뛴다.
-- 이 쿼리에 걸린 폼은 운영자가 해당 질문을 정리한 뒤 3단계를 다시 실행한다.

-- ================================================
-- 1. reserved_key 컬럼
-- ================================================
ALTER TABLE form_questions ADD COLUMN IF NOT EXISTS reserved_key VARCHAR(30);

-- 폼 하나에 같은 표준 질문은 하나만
CREATE UNIQUE INDEX IF NOT EXISTS uk_form_questions_form_reserved_key
    ON form_questions (form_id, reserved_key)
    WHERE reserved_key IS NOT NULL AND deleted_at IS NULL;

-- ================================================
-- 2. 템플릿 '우연한 식탁 표준 폼' + 표준 질문 7개
-- ================================================
INSERT INTO forms (id, gathering_id, is_template, gathering_type, guide_text, created_at, updated_at)
VALUES ('f6000000-0000-0000-0000-000000000341', NULL, TRUE, 'RANDOM_TABLE', NULL, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO form_questions (id, form_id, question_key, type, label, placeholder, required, display_order,
                            options, validation, is_matching_field, is_system_reserved,
                            matching_strategy, matching_weight, reserved_key, created_at, updated_at)
SELECT gen_random_uuid(), 'f6000000-0000-0000-0000-000000000341', seed.question_key, seed.type, seed.label,
       seed.placeholder, seed.required, seed.display_order, seed.options, NULL, seed.is_matching_field, FALSE,
       seed.matching_strategy, seed.matching_weight, seed.reserved_key, now(), now()
  FROM (VALUES
        ('BIRTH_YEAR', 'birth_year', 'NUMBER', '출생연도', '예: 1995', TRUE, 0,
         NULL::jsonb, FALSE, NULL, NULL::numeric),
        ('GENDER', 'gender', 'SINGLE_CHOICE', '성별', NULL, TRUE, 1,
         '{"choices":["MALE","FEMALE"]}'::jsonb, TRUE, 'DIVERSE', 1.00),
        ('MBTI', 'mbti', 'MBTI_INPUT', 'MBTI', NULL, TRUE, 2,
         NULL::jsonb, FALSE, NULL, NULL::numeric),
        ('INTERESTS', 'interests', 'MULTI_CHOICE', '나의 요즘 관심사는? (복수 선택)', NULL, TRUE, 3,
         '{"choices":["영화·드라마","음악","독서·글쓰기","운동 (헬스, 러닝, 클라이밍 등)","맛집·카페 탐방","여행","사진·영상","전시·공연·아트","패션·뷰티","자기계발·공부"]}'::jsonb,
         TRUE, 'OVERLAP', 1.00),
        -- MY_STYLE·WANTED_STYLE은 서로 대조하므로 선택지가 같아야 한다.
        ('MY_STYLE', 'my_style', 'MULTI_CHOICE', '나는 어떤 사람인가요? (복수 선택)', NULL, TRUE, 4,
         '{"choices":["잔잔하고 조용한 편","활기차고 에너지 있는 편","깊은 대화를 좋아함","가볍고 유머 있는 대화를 좋아함","경청을 잘 함","이야기를 이끄는 편"]}'::jsonb,
         FALSE, NULL, NULL::numeric),
        ('WANTED_STYLE', 'wanted_style', 'MULTI_CHOICE', '어떤 사람과 같은 테이블에 앉고 싶으신가요? (복수 선택)', NULL, TRUE, 5,
         '{"choices":["잔잔하고 조용한 편","활기차고 에너지 있는 편","깊은 대화를 좋아함","가볍고 유머 있는 대화를 좋아함","경청을 잘 함","이야기를 이끄는 편"]}'::jsonb,
         FALSE, NULL, NULL::numeric),
        ('DIET', 'diet', 'SHORT_TEXT', '못 먹는 음식이나 식이 제한이 있다면 알려주세요', '예: 갑각류 알레르기, 채식', FALSE, 6,
         NULL::jsonb, FALSE, NULL, NULL::numeric)
       ) AS seed(reserved_key, question_key, type, label, placeholder, required, display_order,
                 options, is_matching_field, matching_strategy, matching_weight)
 WHERE NOT EXISTS (SELECT 1
                     FROM form_questions x
                    WHERE x.form_id = 'f6000000-0000-0000-0000-000000000341'
                      AND x.deleted_at IS NULL
                      AND x.reserved_key = seed.reserved_key);

-- ================================================
-- 3. 기존 RANDOM_TABLE 종류 폼에 표준 질문 채우기
-- ================================================
-- 3-1. 같은 question_key·타입의 기존 질문에 reserved_key를 붙인다(폼·키마다 표시 순서가 가장 앞선 1개).
UPDATE form_questions q
   SET reserved_key = adopt.reserved_key,
       updated_at   = now()
  FROM (SELECT DISTINCT ON (fq.form_id, t.reserved_key) fq.id, t.reserved_key
          FROM form_questions fq
          JOIN forms f ON f.id = fq.form_id AND f.deleted_at IS NULL AND f.is_template = FALSE
          JOIN gatherings g ON g.id = f.gathering_id AND g.gathering_type = 'RANDOM_TABLE' AND g.deleted_at IS NULL
          JOIN form_questions t ON t.form_id = 'f6000000-0000-0000-0000-000000000341'
                               AND t.deleted_at IS NULL
                               AND t.reserved_key IS NOT NULL
                               AND t.question_key = fq.question_key
                               AND t.type = fq.type
         WHERE fq.deleted_at IS NULL
           AND fq.reserved_key IS NULL
           AND NOT EXISTS (SELECT 1
                             FROM form_questions x
                            WHERE x.form_id = fq.form_id
                              AND x.deleted_at IS NULL
                              AND x.reserved_key = t.reserved_key)
         ORDER BY fq.form_id, t.reserved_key, fq.display_order, fq.id) adopt
 WHERE q.id = adopt.id;

-- 3-2. 아직 없는 표준 질문을 템플릿에서 복사해 폼 맨 뒤에 추가한다.
INSERT INTO form_questions (id, form_id, question_key, type, label, placeholder, required, display_order,
                            options, validation, is_matching_field, is_system_reserved,
                            matching_strategy, matching_weight, reserved_key, created_at, updated_at)
SELECT gen_random_uuid(), f.id, t.question_key, t.type, t.label, t.placeholder, t.required,
       last_order.value + 1 + t.display_order, t.options, t.validation, t.is_matching_field, FALSE,
       t.matching_strategy, t.matching_weight, t.reserved_key, now(), now()
  FROM forms f
  JOIN gatherings g ON g.id = f.gathering_id AND g.gathering_type = 'RANDOM_TABLE' AND g.deleted_at IS NULL
  JOIN form_questions t ON t.form_id = 'f6000000-0000-0000-0000-000000000341'
                       AND t.deleted_at IS NULL
                       AND t.reserved_key IS NOT NULL
  CROSS JOIN LATERAL (SELECT COALESCE(MAX(o.display_order), -1) AS value
                        FROM form_questions o
                       WHERE o.form_id = f.id
                         AND o.deleted_at IS NULL) last_order
 WHERE f.deleted_at IS NULL
   AND f.is_template = FALSE
   AND NOT EXISTS (SELECT 1
                     FROM form_questions x
                    WHERE x.form_id = f.id
                      AND x.deleted_at IS NULL
                      AND (x.reserved_key = t.reserved_key OR x.question_key = t.question_key));
