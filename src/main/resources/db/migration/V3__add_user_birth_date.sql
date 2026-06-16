-- 회원 생년월일 컬럼 추가 (KAN-257)
-- 나이가 필요한 응답에서 생년월일 기준 만 나이를 계산하기 위한 컬럼이다.
-- 기존 회원은 생년월일 미보유(NULL)이며, 응답 시 기존 age 컬럼으로 폴백한다.
ALTER TABLE users ADD COLUMN birth_date DATE;
