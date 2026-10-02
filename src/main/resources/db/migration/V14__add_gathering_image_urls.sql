-- V14: 게더링 종류 상세 사진 여러 장 (KAN-371)
-- 배열 순서 = 노출 순서. thumbnail_url은 대표 사진(목록·큐레이션·캐러셀)으로 그대로 둔다.
ALTER TABLE gatherings
    ADD COLUMN IF NOT EXISTS image_urls JSONB NOT NULL DEFAULT '[]'::jsonb;
