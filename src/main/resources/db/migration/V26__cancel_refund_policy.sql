-- KAN-418: 취소 환불 정책(마감 전 100%·마감 후 50%). 보증금 반환(예정) 금액. amount보다 작으면 나머지는 몰수.
ALTER TABLE deposits ADD COLUMN return_amount INTEGER;

-- 기존 반환 건은 전액 반환이었다.
UPDATE deposits SET return_amount = amount WHERE status IN ('RETURN_PENDING', 'RETURNED');
