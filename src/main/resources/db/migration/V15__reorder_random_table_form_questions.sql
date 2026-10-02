-- V15: 우연한 식탁 신청서(form 98049b28-f404-fbe0-6609-edfedbec7ca7)를 최신 구글폼 순서로 재배치하고 중복 질문을 지운다. (KAN-388)
--
-- V2 운영 폼 질문(나이·나는 어떤 사람인가요?·어떤 사람을 만나고 싶으신가요?)과 V6 표준 질문
-- (출생연도·MY_STYLE·WANTED_STYLE)이 섞여 같은 걸 두 번 묻고, 개인정보 동의가 중간에 있었다.
-- - soft delete: age(출생연도와 중복), personality(my_style과 문구·선택지 동일), desired_people(wanted_style로 대체)
--   기존 답변(application_answers)은 그대로 둔다. 이 셋은 커스텀 매칭 필드였지만 출생연도 나이 차이·MY_STYLE·WANTED_STYLE이
--   엔진 기본 항목이라 중복 신호만 빠진다.
-- - wanted_style 문구를 구글폼 문구로 바꾼다. 선택지는 매칭 엔진이 상대 MY_STYLE과 겹침으로 점수를 내므로 그대로 둔다.
-- - birth_year 문구를 구글폼 문구 '나이'로 바꾼다. 답은 그대로 정수 출생연도다. 템플릿 폼(f6000000-…-000000000341)은 그대로.
-- - 개인정보 동의를 맨 끝으로. 구글폼의 '참석 가능 지역'은 회차 장소로 정해지므로 추가하지 않는다.
-- form_id + question_key 로만 갱신하므로 이 폼이 없는 환경에서는 0건. display_order에는 유니크 제약이 없다.

UPDATE form_questions
   SET deleted_at = now(),
       updated_at = now()
 WHERE form_id = '98049b28-f404-fbe0-6609-edfedbec7ca7'
   AND question_key IN ('age', 'personality', 'desired_people')
   AND deleted_at IS NULL;

UPDATE form_questions q
   SET display_order = v.display_order,
       updated_at    = now()
  FROM (VALUES ('name', 1),
               ('phone', 2),
               ('email', 3),
               ('instagram_id', 4),
               ('birth_year', 5),
               ('gender', 6),
               ('job', 7),
               ('mbti', 8),
               ('interests', 9),
               ('my_style', 10),
               ('wanted_style', 11),
               ('budget', 12),
               ('diet', 13),
               ('privacy_consent', 14)) AS v(question_key, display_order)
 WHERE q.form_id = '98049b28-f404-fbe0-6609-edfedbec7ca7'
   AND q.question_key = v.question_key
   AND q.deleted_at IS NULL;

UPDATE form_questions
   SET label      = '어떤 사람을 만나고 싶으신가요? (복수 선택)',
       updated_at = now()
 WHERE form_id = '98049b28-f404-fbe0-6609-edfedbec7ca7'
   AND question_key = 'wanted_style'
   AND deleted_at IS NULL;

UPDATE form_questions
   SET label      = '나이',
       updated_at = now()
 WHERE form_id = '98049b28-f404-fbe0-6609-edfedbec7ca7'
   AND question_key = 'birth_year'
   AND deleted_at IS NULL;
