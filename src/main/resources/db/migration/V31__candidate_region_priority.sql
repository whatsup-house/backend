-- V31: 우연한 식탁 희망 지역 지망 순위. 신청 때 고른 순서가 1지망부터 저장된다.
-- 이미 저장된 희망 지역은 순서를 모르므로 지역 선언 순서(홍대/합정·잠실·강남/신논현·을지로/종로)로 매긴다(멱등).
ALTER TABLE application_candidate_regions ADD COLUMN IF NOT EXISTS priority INTEGER;

UPDATE application_candidate_regions r
   SET priority = ranked.rn
  FROM (SELECT application_id, region,
               ROW_NUMBER() OVER (PARTITION BY application_id
                                  ORDER BY CASE region WHEN '홍대/합정' THEN 1 WHEN '잠실' THEN 2
                                                       WHEN '강남/신논현' THEN 3 WHEN '을지로/종로' THEN 4 ELSE 5 END,
                                           region) AS rn
          FROM application_candidate_regions) ranked
 WHERE r.application_id = ranked.application_id AND r.region = ranked.region AND r.priority IS NULL;
