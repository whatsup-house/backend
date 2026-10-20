-- V19: 기간제 멤버십 이용권과 해커톤 가격. (KAN-401)
ALTER TABLE ticket_products
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'COUNT',
    ADD COLUMN IF NOT EXISTS period_months INTEGER;

-- 멤버십: 활성화 날짜 + period_months개월까지 사용(만료일 포함). 매칭 실패 연장분은 extended_days에 누적.
ALTER TABLE ticket_passes
    ADD COLUMN IF NOT EXISTS valid_until DATE,
    ADD COLUMN IF NOT EXISTS extended_days INTEGER NOT NULL DEFAULT 0;

-- 기존 상품은 판매 중단(soft delete). 이미 산 이용권은 product_name 스냅샷으로 계속 보인다.
UPDATE ticket_products SET deleted_at = NOW(), updated_at = NOW() WHERE deleted_at IS NULL;

INSERT INTO ticket_products (id, name, session_count, price, kind, period_months, created_at, updated_at) VALUES
    (gen_random_uuid(), '우연한 식탁 1회권',     1,   9900, 'COUNT',  NULL, NOW(), NOW()),
    (gen_random_uuid(), '우연한 식탁 멤버십 1개월', 0,  35000, 'PERIOD', 1,    NOW(), NOW()),
    (gen_random_uuid(), '우연한 식탁 멤버십 3개월', 0,  75000, 'PERIOD', 3,    NOW(), NOW()),
    (gen_random_uuid(), '우연한 식탁 멤버십 6개월', 0, 120000, 'PERIOD', 6,    NOW(), NOW());
