-- V24: 심사용 숨김 모임 종류 + 표준 폼 식사 가격대 질문 PRICE_RANGE. (KAN-407)
--
-- 1. gatherings.hidden: 숨김 종류는 목록·홈·추천·다음 모집 알림에서 빠지고 체험(데모) 회원에게만 보인다. 직접 링크는 열린다.
-- 2. 표준 폼 질문 PRICE_RANGE(복수 선택, 매칭 미사용): 템플릿 폼과 기존 RANDOM_TABLE 종류 폼에 reserved_key 기준으로 멱등 추가.
--    식당 배정 때 테이블 멤버 답의 교집합에 맞는 Venue.price_range를 먼저 배정한다.
--    선택지 문구는 PriceRangeOption과 같아야 한다. 필수가 아니라 기존 신청 흐름은 막지 않는다.

ALTER TABLE gatherings ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT false;

-- 템플릿 '우연한 식탁 표준 폼'(V6)에 추가
INSERT INTO form_questions (id, form_id, question_key, type, label, placeholder, required, display_order,
                            options, validation, is_matching_field, is_system_reserved,
                            matching_strategy, matching_weight, reserved_key, created_at, updated_at)
SELECT gen_random_uuid(), 'f6000000-0000-0000-0000-000000000341', 'price_range', 'MULTI_CHOICE',
       '선호하는 식사 가격대는? (복수 선택)', NULL, FALSE, 7,
       '{"choices":["10,000~20,000원","20,000~30,000원","30,000원 이상 (상관 없음)"]}'::jsonb,
       NULL, FALSE, FALSE, NULL, NULL, 'PRICE_RANGE', now(), now()
 WHERE NOT EXISTS (SELECT 1
                     FROM form_questions x
                    WHERE x.form_id = 'f6000000-0000-0000-0000-000000000341'
                      AND x.deleted_at IS NULL
                      AND x.reserved_key = 'PRICE_RANGE');

-- 기존 RANDOM_TABLE 종류 폼에 템플릿 질문을 복사해 폼 맨 뒤에 추가 (V6 3-2와 같은 방식)
INSERT INTO form_questions (id, form_id, question_key, type, label, placeholder, required, display_order,
                            options, validation, is_matching_field, is_system_reserved,
                            matching_strategy, matching_weight, reserved_key, created_at, updated_at)
SELECT gen_random_uuid(), f.id, t.question_key, t.type, t.label, t.placeholder, t.required,
       last_order.value + 1, t.options, t.validation, t.is_matching_field, FALSE,
       t.matching_strategy, t.matching_weight, t.reserved_key, now(), now()
  FROM forms f
  JOIN gatherings g ON g.id = f.gathering_id AND g.gathering_type = 'RANDOM_TABLE' AND g.deleted_at IS NULL
  JOIN form_questions t ON t.form_id = 'f6000000-0000-0000-0000-000000000341'
                       AND t.deleted_at IS NULL
                       AND t.reserved_key = 'PRICE_RANGE'
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
