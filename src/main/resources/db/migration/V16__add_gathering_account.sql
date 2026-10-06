-- V16: 게더링 입금 계좌 정보(은행·계좌번호·예금주). 모두 선택 입력. (KAN-390)
ALTER TABLE gatherings
    ADD COLUMN IF NOT EXISTS account_bank VARCHAR(50),
    ADD COLUMN IF NOT EXISTS account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS account_holder VARCHAR(50);
