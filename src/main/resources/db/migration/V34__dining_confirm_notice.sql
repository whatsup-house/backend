-- V34: 우연한 식탁 확정 안내 강화 (KAN-438)
-- - reservation_name / gather_at: 식당 배정 시 예약명(와썹하우스)·집합 시각(회차 시작 10분 전). 이미 식당이 배정된 테이블도 같은 규칙으로 채운다.
-- - chat_retry_count: 확정 후 채팅방이 없는 테이블의 자동 재시도 횟수(최대 3, 스케줄러 5분 주기).
-- - posted_content_ids: 채팅방에 올린 대화 콘텐츠(dining_contents.id) 목록. 참가자 테이블 상세가 같은 콘텐츠를 보여 준다.
-- - dining_contents 시드: 성향(MY_STYLE·WANTED_STYLE 선택지) 맞춤 주제. interest_tag에 성향 선택지 값을 넣는다(관심사 선택지와 겹치지 않음).

ALTER TABLE dining_tables
    ADD COLUMN IF NOT EXISTS reservation_name   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS gather_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS chat_retry_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS posted_content_ids JSONB;

UPDATE dining_tables t
   SET reservation_name = '와썹하우스',
       gather_at        = s.event_date + s.start_time - INTERVAL '10 minutes'
  FROM gathering_sessions s
 WHERE s.id = t.session_id
   AND t.venue_id IS NOT NULL
   AND t.reservation_name IS NULL;

INSERT INTO dining_contents (kind, interest_tag, body)
SELECT seed.kind, seed.interest_tag, seed.body
  FROM (VALUES
        ('TOPIC', '잔잔하고 조용한 편', '혼자만의 시간을 가장 편하게 보내는 방법은 무엇인가요?'),
        ('TOPIC', '잔잔하고 조용한 편', '요즘 마음이 편안해지는 장소나 순간이 있나요?'),
        ('TOPIC', '활기차고 에너지 있는 편', '최근에 가장 신나게 놀았던 날은 언제였나요?'),
        ('TOPIC', '활기차고 에너지 있는 편', '이번 달 안에 꼭 해 보고 싶은 새로운 도전이 있나요?'),
        ('TOPIC', '깊은 대화를 좋아함', '요즘 가장 많이 고민하고 있는 질문은 무엇인가요?'),
        ('TOPIC', '깊은 대화를 좋아함', '나에게 큰 영향을 준 사람이나 경험을 이야기해 볼까요?'),
        ('TOPIC', '가볍고 유머 있는 대화를 좋아함', '최근에 본 가장 웃긴 영상이나 짤은 무엇인가요?'),
        ('TOPIC', '가볍고 유머 있는 대화를 좋아함', '살면서 겪은 가장 황당한 에피소드가 있나요?'),
        ('TOPIC', '경청을 잘 함', '요즘 누군가에게 꼭 듣고 싶은 이야기가 있나요?'),
        ('TOPIC', '경청을 잘 함', '최근에 들은 말 중 오래 기억에 남는 한마디는요?'),
        ('TOPIC', '이야기를 이끄는 편', '이 자리의 모두에게 하나씩 묻고 싶은 질문이 있다면요?'),
        ('TOPIC', '이야기를 이끄는 편', '처음 만난 사람과 금방 친해지는 나만의 비결이 있나요?')
       ) AS seed(kind, interest_tag, body)
 WHERE NOT EXISTS (SELECT 1 FROM dining_contents c WHERE c.interest_tag = seed.interest_tag AND c.body = seed.body);
