-- KAN-161: provider별 지도 URL 컬럼 추가
-- 기존 locations.map_url 은 하위 호환용 legacy/fallback 컬럼으로 유지한다.
ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS naver_map_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS kakao_map_url VARCHAR(500);
