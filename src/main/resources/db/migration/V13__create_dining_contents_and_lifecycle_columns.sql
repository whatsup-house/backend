-- V13: 우연한 식탁 리마인드·대화 콘텐츠·체크인·노쇼 (KAN-349)
-- 설계: docs/superpowers/specs/2026-09-29-dining-table-v2-design.md 2.8, 4.9
--
-- - dining_contents: 회차 시작 시각에 테이블 채팅방에 올리는 대화 주제(TOPIC)·아이스브레이킹(ICEBREAKER) 시드.
--   interest_tag는 표준 폼 INTERESTS 선택지 값(V6)과 같다. NULL이면 범용. 관리 UI는 없다(CON-04는 P1).
-- - dining_tables.reminder_sent_at / contents_posted_at: 24시간 전 리마인드·대화 콘텐츠 게시를 테이블마다 1회만 하기 위한 표시.
-- - gathering_sessions.closing_processed_at: 회차 종료 처리(노쇼 후보·테이블 DONE·피드백 요청)를 회차마다 1회만 하기 위한 표시.
-- - V11(KAN-347)·V12(KAN-350)와 독립적이다.

CREATE TABLE IF NOT EXISTS dining_contents (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    kind         VARCHAR(20)  NOT NULL,   -- TOPIC|ICEBREAKER
    interest_tag VARCHAR(100),
    body         TEXT         NOT NULL
);

ALTER TABLE dining_tables
    ADD COLUMN IF NOT EXISTS reminder_sent_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS contents_posted_at TIMESTAMP;

ALTER TABLE gathering_sessions
    ADD COLUMN IF NOT EXISTS closing_processed_at TIMESTAMP;

-- 이미 시드가 있으면(수동 적재 등) 다시 넣지 않는다.
INSERT INTO dining_contents (kind, interest_tag, body)
SELECT seed.kind, seed.interest_tag, seed.body
  FROM (VALUES
        -- 범용 대화 주제
        ('TOPIC', NULL, '요즘 가장 기다려지는 일은 무엇인가요?'),
        ('TOPIC', NULL, '최근에 새로 시작해 본 것이 있다면 소개해 주세요.'),
        ('TOPIC', NULL, '하루 중 가장 좋아하는 시간대와 그 이유는요?'),
        ('TOPIC', NULL, '어린 시절 꿈과 지금의 나는 얼마나 닮았나요?'),
        ('TOPIC', NULL, '올해 꼭 해 보고 싶은 한 가지를 꼽는다면요?'),
        ('TOPIC', NULL, '스트레스를 푸는 나만의 방법이 있나요?'),
        ('TOPIC', NULL, '누군가에게 추천하고 싶은 동네나 장소가 있나요?'),
        ('TOPIC', NULL, '최근에 크게 웃었던 순간을 떠올려 볼까요?'),
        ('TOPIC', NULL, '일주일 휴가가 갑자기 생긴다면 무엇을 하고 싶나요?'),
        ('TOPIC', NULL, '요즘 나를 가장 설레게 하는 작은 즐거움은 무엇인가요?'),
        -- 관심사별 대화 주제
        ('TOPIC', '영화·드라마', '인생 영화나 드라마 한 편을 꼽고 이유를 나눠 볼까요?'),
        ('TOPIC', '영화·드라마', '요즘 정주행 중인 작품이 있다면 추천해 주세요.'),
        ('TOPIC', '음악', '요즘 가장 많이 듣는 노래는 무엇인가요?'),
        ('TOPIC', '음악', '기억에 남는 공연이나 페스티벌 경험이 있나요?'),
        ('TOPIC', '독서·글쓰기', '최근에 읽은 책 중 한 문장을 소개한다면요?'),
        ('TOPIC', '독서·글쓰기', '글로 남겨 두고 싶은 요즘의 생각이 있나요?'),
        ('TOPIC', '운동 (헬스, 러닝, 클라이밍 등)', '요즘 즐기는 운동과 시작하게 된 계기는요?'),
        ('TOPIC', '운동 (헬스, 러닝, 클라이밍 등)', '함께 해 보고 싶은 운동이나 도전 목표가 있나요?'),
        ('TOPIC', '맛집·카페 탐방', '최근에 발견한 최고의 맛집이나 카페는 어디인가요?'),
        ('TOPIC', '맛집·카페 탐방', '나만 알고 싶은 단골 가게가 있나요?'),
        ('TOPIC', '여행', '가장 기억에 남는 여행지와 그곳의 한 장면은요?'),
        ('TOPIC', '여행', '다음 여행지로 점찍어 둔 곳이 있나요?'),
        ('TOPIC', '사진·영상', '휴대폰 앨범에서 가장 아끼는 사진은 어떤 장면인가요?'),
        ('TOPIC', '사진·영상', '요즘 찍고 싶은 사진이나 영상이 있나요?'),
        ('TOPIC', '전시·공연·아트', '최근에 본 전시나 공연 중 인상 깊었던 것은요?'),
        ('TOPIC', '전시·공연·아트', '좋아하는 작가나 아티스트를 소개해 주세요.'),
        ('TOPIC', '패션·뷰티', '요즘 가장 자주 손이 가는 아이템은 무엇인가요?'),
        ('TOPIC', '패션·뷰티', '나를 가장 잘 표현하는 스타일은 어떤 모습인가요?'),
        ('TOPIC', '자기계발·공부', '요즘 배우고 있거나 배우고 싶은 것이 있나요?'),
        ('TOPIC', '자기계발·공부', '꾸준히 이어 가고 있는 루틴이 있다면 소개해 주세요.'),
        -- 아이스브레이킹
        ('ICEBREAKER', NULL, '돌아가며 오늘 불리고 싶은 이름과 요즘 기분을 날씨로 표현해 볼까요?'),
        ('ICEBREAKER', NULL, '두 가지 진실과 한 가지 거짓말: 나에 대한 문장 3개 중 거짓을 맞혀 보세요.'),
        ('ICEBREAKER', NULL, '오늘 메뉴를 보고 떠오르는 추억 하나씩 이야기해 볼까요?'),
        ('ICEBREAKER', NULL, '나를 세 단어로 소개한다면? 돌아가며 말해 보세요.'),
        ('ICEBREAKER', NULL, '이번 주 가장 잘한 일 하나를 자랑해 볼까요?')
       ) AS seed(kind, interest_tag, body)
 WHERE NOT EXISTS (SELECT 1 FROM dining_contents);
