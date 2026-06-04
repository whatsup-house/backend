-- KAN-161: legacy map_url 컬럼 제거
-- 제거 전, 기존 map_url 값을 provider별 컬럼으로 백필하여 데이터 유실을 방지한다.
-- (네이버/카카오 도메인이 아닌 기존 값은 신규 provider 정책상 이관하지 않고 폐기한다.)
UPDATE locations
SET naver_map_url = map_url
WHERE naver_map_url IS NULL
  AND map_url IS NOT NULL
  AND (map_url ILIKE '%naver.%' OR map_url ILIKE '%map.naver%');

UPDATE locations
SET kakao_map_url = map_url
WHERE kakao_map_url IS NULL
  AND map_url IS NOT NULL
  AND (map_url ILIKE '%kakao%' OR map_url ILIKE '%kko.kakao%');

ALTER TABLE locations DROP COLUMN map_url;
