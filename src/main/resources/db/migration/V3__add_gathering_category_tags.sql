-- 게더링 카테고리/태그 컬럼 추가 (KAN-304)
ALTER TABLE gatherings ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE gatherings ADD COLUMN IF NOT EXISTS tags JSONB;
