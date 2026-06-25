-- 15반 1학기 종강파티: 운영 DB에 등록된 게더링/신청폼 데이터를 V3 시드로 고정한다.
INSERT INTO locations (id, name, address, naver_map_url, kakao_map_url, status, max_capacity, memo, created_at, updated_at)
VALUES (
    '0266fc6b-5cf3-41da-a4b7-2a703b2fb526',
    '닭가대표',
    '서울 강남구 테헤란로21길 25',
    'https://naver.me/GEXkrIDp',
    'https://place.map.kakao.com/1396281814',
    'ACTIVE',
    20,
    'ㅈㅁㅌ',
    '2026-06-25 07:16:32.218813',
    '2026-06-25 07:16:32.218813'
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    address = EXCLUDED.address,
    naver_map_url = EXCLUDED.naver_map_url,
    kakao_map_url = EXCLUDED.kakao_map_url,
    status = EXCLUDED.status,
    max_capacity = EXCLUDED.max_capacity,
    memo = EXCLUDED.memo,
    updated_at = EXCLUDED.updated_at,
    deleted_at = NULL;

INSERT INTO gatherings (
    id, title, description, how_to_run, tags, location_id, event_date, start_time, end_time,
    price, max_attendees, gathering_type, status, thumbnail_url, is_curated, curated_rank, created_at, updated_at
)
VALUES (
    '30a84e09-ecb0-4803-b597-60e4f22e0dba',
    '15반 1학기 종강파티',
    'SSAFY 15반의 1학기를 마무리하며 함께 웃고, 먹고, 추억을 나누는 종강파티입니다.
열심히 달려온 우리 모두를 위해 준비한 자리인 만큼 편하게 즐기고 서로의 이야기를 나눠보세요!',
    '["15반 반장의 개회사", "웃고 떠들며 맛있는 식사", "권태혁의 장기자랑", "15반 CA의 폐회사"]'::jsonb,
    '["파티", "푸드"]'::jsonb,
    '0266fc6b-5cf3-41da-a4b7-2a703b2fb526',
    '2026-06-26',
    '18:30',
    '00:00',
    0,
    30,
    'RANDOM_TABLE',
    'OPEN',
    'https://mcvtfdwsxmtqgxzlfqjx.supabase.co/storage/v1/object/public/whatsup-images/gatherings/ssafy15-party.png',
    FALSE,
    0,
    '2026-06-25 07:41:38.272224',
    '2026-06-25 07:41:38.272224'
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    how_to_run = EXCLUDED.how_to_run,
    tags = EXCLUDED.tags,
    location_id = EXCLUDED.location_id,
    event_date = EXCLUDED.event_date,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    price = EXCLUDED.price,
    max_attendees = EXCLUDED.max_attendees,
    gathering_type = EXCLUDED.gathering_type,
    status = EXCLUDED.status,
    thumbnail_url = EXCLUDED.thumbnail_url,
    is_curated = EXCLUDED.is_curated,
    curated_rank = EXCLUDED.curated_rank,
    updated_at = EXCLUDED.updated_at,
    deleted_at = NULL;

INSERT INTO forms (id, gathering_id, is_template, gathering_type, guide_text, created_at, updated_at)
VALUES (
    'b91b04be-1db8-4083-90a3-da6c7b7cfbc4',
    '30a84e09-ecb0-4803-b597-60e4f22e0dba',
    FALSE,
    NULL,
    NULL,
    '2026-06-25 07:41:38.279559',
    '2026-06-25 07:41:38.279559'
)
ON CONFLICT (id) DO UPDATE SET
    gathering_id = EXCLUDED.gathering_id,
    is_template = EXCLUDED.is_template,
    gathering_type = EXCLUDED.gathering_type,
    guide_text = EXCLUDED.guide_text,
    updated_at = EXCLUDED.updated_at,
    deleted_at = NULL;

INSERT INTO form_questions (
    id, form_id, question_key, type, label, placeholder, required, display_order, options, validation,
    is_matching_field, is_system_reserved, matching_strategy, matching_weight, created_at, updated_at
)
VALUES
    ('54f4909d-53f3-4f04-853b-d1f788769f90', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'name', 'SHORT_TEXT', '이름', NULL, TRUE, 0, NULL, NULL, FALSE, TRUE, NULL, NULL, '2026-06-25 07:41:38.288839', '2026-06-25 07:41:38.288839'),
    ('061aba65-e895-42f0-b7e3-edf13cc27772', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'phone', 'SHORT_TEXT', '연락처', NULL, TRUE, 1, NULL, NULL, FALSE, TRUE, NULL, NULL, '2026-06-25 07:41:38.295073', '2026-06-25 07:41:38.295073'),
    ('479ef00b-3ced-454b-b70f-908079211248', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'email', 'SHORT_TEXT', '이메일', NULL, TRUE, 2, NULL, NULL, FALSE, TRUE, NULL, NULL, '2026-06-25 07:41:38.295412', '2026-06-25 07:41:38.295412'),
    ('2e048c63-6163-4c7b-bf5f-59d51b345f3c', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'mbti', 'MBTI_INPUT', 'MBTI', NULL, TRUE, 3, NULL, NULL, TRUE, FALSE, 'DIVERSE', 1.00, '2026-06-25 07:48:37.808858', '2026-06-25 07:48:47.847727'),
    ('03ab60ae-ab5e-4507-9ed9-f90575ec5046', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'interests', 'MULTI_CHOICE', '나의 요즘 관심사는? (복수 선택)', NULL, TRUE, 4, '{"choices": ["영화·드라마", "음악", "독서·글쓰기", "운동 (헬스", "러닝", "클라이밍 등)", "맛집·카페 탐방", "여행", "사진·영상", "전시·공연·아트", "패션·뷰티", "자기계발·공부"]}'::jsonb, NULL, TRUE, FALSE, 'OVERLAP', 1.00, '2026-06-25 07:43:11.535460', '2026-06-25 07:48:47.860645'),
    ('c55d2124-a83c-4ec6-872c-2c7dbe1e95e0', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'q_mqt787r5b9w2', 'MULTI_CHOICE', '나는 어떤 사람인가요? (복수 선택)', NULL, TRUE, 5, '{"choices": ["잔잔하고 조용한 편", "활기차고 에너지 있는 편", "깊은 대화를 좋아함", "가볍고 유머 있는 대화를 좋아함", "경청을 잘 함", "이야기를 이끄는 편"]}'::jsonb, NULL, TRUE, FALSE, 'OVERLAP', 1.00, '2026-06-25 07:46:50.589283', '2026-06-25 07:48:46.591181'),
    ('d6dd5c02-40b9-4d85-9f00-8dfb259480e1', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'q_mqt7a17oczoq', 'MULTI_CHOICE', '어떤 사람을 만나고 싶은가요?(복수 선택)', NULL, TRUE, 6, '{"choices": ["나와 비슷한 사람", "나와 다른 자극을 주는 사람", "깊은 대화가 되는 사람", "편하고 유머 있는 사람", "열정 있는 사람", "잔잔하고 안정적인 사람"]}'::jsonb, NULL, TRUE, FALSE, 'OVERLAP', 1.00, '2026-06-25 07:48:15.532684', '2026-06-25 07:48:45.149293'),
    ('3189217f-dbae-4ac6-bb0a-7ac631fe51f6', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'q_mqt7cz6fs77p', 'SINGLE_CHOICE', '오늘 얼마나 마실껀가요?', NULL, TRUE, 7, '{"choices": ["음료수", "3잔", "1병", "2병 이상"]}'::jsonb, NULL, TRUE, FALSE, 'SAME', 1.00, '2026-06-25 07:50:32.753411', '2026-06-25 07:50:32.753411'),
    ('e15e267d-4b25-4e29-b529-23b76c34c45e', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'q_mqt7e9mmjy62', 'SINGLE_CHOICE', '오늘 목표 귀가시간은?', NULL, TRUE, 8, '{"choices": ["밥 다 먹으면 바로", "9시 쯤?", "막차까지"]}'::jsonb, NULL, TRUE, FALSE, 'DIVERSE', 1.00, '2026-06-25 07:51:32.971978', '2026-06-25 07:51:32.971978'),
    ('e1199452-6fa6-4b6b-8215-ab1ce45bf1dc', 'b91b04be-1db8-4083-90a3-da6c7b7cfbc4', 'q_mqt7h45gj10d', 'SINGLE_CHOICE', '2학기 가서 우리 반 친구를 복도에서 만나면?', NULL, TRUE, 9, '{"choices": ["쌩깜", "어색한 손인사", "난 싸탈해서 없을 예정ㅋ", "15반 야호-"]}'::jsonb, NULL, TRUE, FALSE, 'DIVERSE', 1.00, '2026-06-25 07:53:45.825841', '2026-06-25 07:53:45.825841')
ON CONFLICT (id) DO UPDATE SET
    form_id = EXCLUDED.form_id,
    question_key = EXCLUDED.question_key,
    type = EXCLUDED.type,
    label = EXCLUDED.label,
    placeholder = EXCLUDED.placeholder,
    required = EXCLUDED.required,
    display_order = EXCLUDED.display_order,
    options = EXCLUDED.options,
    validation = EXCLUDED.validation,
    is_matching_field = EXCLUDED.is_matching_field,
    is_system_reserved = EXCLUDED.is_system_reserved,
    matching_strategy = EXCLUDED.matching_strategy,
    matching_weight = EXCLUDED.matching_weight,
    updated_at = EXCLUDED.updated_at,
    deleted_at = NULL;

UPDATE carousel_slides
SET sort_order = 3,
    updated_at = NOW()
WHERE id = 'd0000001-0000-0000-0000-000000000001';

UPDATE carousel_slides
SET sort_order = 4,
    updated_at = NOW()
WHERE id = 'd0000001-0000-0000-0000-000000000007';

UPDATE carousel_slides
SET sort_order = 5,
    updated_at = NOW()
WHERE id = 'd0000001-0000-0000-0000-000000000004';

UPDATE carousel_slides
SET sort_order = 6,
    updated_at = NOW()
WHERE id = 'd0000001-0000-0000-0000-000000000003';

UPDATE carousel_slides
SET sort_order = 7,
    updated_at = NOW()
WHERE id = 'd0000001-0000-0000-0000-000000000005';

INSERT INTO carousel_slides (id, type, title, content, image_url, gathering_id, sort_order, is_active, created_at, updated_at)
SELECT
    'd0000001-0000-0000-0000-000000000008',
    'GATHERING',
    '15반 1학기 종강파티',
    NULL,
    'https://mcvtfdwsxmtqgxzlfqjx.supabase.co/storage/v1/object/public/whatsup-images/gatherings/ssafy15-party.png',
    g.id,
    2,
    TRUE,
    NOW(),
    NOW()
FROM gatherings g
WHERE g.title = '15반 1학기 종강파티'
  AND g.deleted_at IS NULL
ORDER BY g.event_date ASC, g.created_at ASC
LIMIT 1
ON CONFLICT (id) DO UPDATE SET
    type = EXCLUDED.type,
    title = EXCLUDED.title,
    content = EXCLUDED.content,
    image_url = EXCLUDED.image_url,
    gathering_id = EXCLUDED.gathering_id,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();
