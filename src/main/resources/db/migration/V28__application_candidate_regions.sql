-- V28: 우연한 식탁 신청 희망 지역(복수 선택). (KAN-430)
--
-- 1. application_candidate_regions: 신청 × 지역. 지역은 DiningRegion 라벨 문자열을 그대로 저장한다.
-- 2. 백필: 희망 회차가 있는 우연한 식탁 신청.
--    희망 회차 장소명이 4개 지역 라벨과 같으면 그 지역들, 하나도 안 맞으면 4개 지역 전부.
--    이미 행이 있는 신청·지역은 건너뛰므로 다시 돌려도 바뀌지 않는다.
-- 라벨 4개는 DiningRegion(KAN-430) 선언과 같다. 이 파일은 그 시점 스냅샷이다.

CREATE TABLE IF NOT EXISTS application_candidate_regions (
    application_id UUID        NOT NULL REFERENCES applications(id),
    region         VARCHAR(50) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, region)
);

-- 2-1. 희망 회차 장소명이 지역 라벨과 같은 지역
INSERT INTO application_candidate_regions (application_id, region)
SELECT DISTINCT c.application_id, TRIM(l.name)
  FROM application_candidate_sessions c
  JOIN applications a ON a.id = c.application_id
  JOIN gatherings g ON g.id = a.gathering_id
  JOIN gathering_sessions s ON s.id = c.session_id
  JOIN locations l ON l.id = s.location_id
 WHERE g.gathering_type = 'RANDOM_TABLE'
   AND TRIM(l.name) IN ('홍대/합정', '잠실', '강남/신논현', '을지로/종로')
   AND NOT EXISTS (SELECT 1 FROM application_candidate_regions x
                    WHERE x.application_id = c.application_id AND x.region = TRIM(l.name));

-- 2-2. 하나도 안 맞은(아직 지역이 없는) 신청은 4개 지역 전부
INSERT INTO application_candidate_regions (application_id, region)
SELECT a.id, r.label
  FROM applications a
  JOIN gatherings g ON g.id = a.gathering_id
 CROSS JOIN (VALUES ('홍대/합정'), ('잠실'), ('강남/신논현'), ('을지로/종로')) AS r(label)
 WHERE g.gathering_type = 'RANDOM_TABLE'
   AND EXISTS (SELECT 1 FROM application_candidate_sessions c WHERE c.application_id = a.id)
   AND NOT EXISTS (SELECT 1 FROM application_candidate_regions x WHERE x.application_id = a.id);
