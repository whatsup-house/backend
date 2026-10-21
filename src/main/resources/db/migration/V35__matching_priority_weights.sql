-- V35: 매칭 우선순위 가중치(PRD MAT-04, KAN-435). 연령대 5 · 성비 4 · MBTI 3 · 관심사 2 · 성향 1 · 커스텀 1.
-- 기본 행이 V7 초기값(전부 1.0, 연령대 키 없음) 그대로일 때만 바꾼다 — 관리자가 직접 고친 가중치는 건드리지 않는다(멱등).
UPDATE matching_rule_settings
   SET weights = '{"ageBand": 5, "gender": 4, "mbti": 3, "interests": 2, "wantedStyle": 1, "custom": 1}'::jsonb
 WHERE NOT (weights ? 'ageBand')
   AND (weights->>'gender')::numeric = 1
   AND (weights->>'mbti')::numeric = 1
   AND (weights->>'interests')::numeric = 1
   AND (weights->>'wantedStyle')::numeric = 1
   AND (weights->>'custom')::numeric = 1;
