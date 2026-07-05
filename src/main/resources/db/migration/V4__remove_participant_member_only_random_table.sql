-- V4: 우연한 식탁 회원 전용 전환 및 Participant 계층 제거
--
-- 배경: 비회원 이용권 구매 폐기 결정에 따라 신청(applications)·이용권(ticket_passes)의
-- 소유 주체를 participant 경유에서 user 직결로 전환한다.
-- participants 테이블과 기존 participant_id 컬럼은 롤백 대비를 위해 이번 릴리스에서는
-- 유지하고, 다음 릴리스에서 제거한다. (V5 예정)
--
-- 사전 조건(운영 확인 필수): 비회원 소유 ACTIVE/PENDING 이용권이 없어야 한다.
--   SELECT count(*) FROM ticket_passes tp
--     JOIN participants p ON tp.participant_id = p.id
--    WHERE p.user_id IS NULL AND tp.status IN ('ACTIVE','PENDING') AND tp.deleted_at IS NULL;

-- 1. users: 우연한 식탁 자격 컬럼 이관
ALTER TABLE users ADD COLUMN IF NOT EXISTS random_table_eligibility VARCHAR(20);

UPDATE users u
   SET random_table_eligibility = p.random_table_eligibility
  FROM participants p
 WHERE p.user_id = u.id
   AND p.deleted_at IS NULL
   AND u.random_table_eligibility IS NULL;

-- participant 차단(BLOCKED)은 회원 계정 정지(SUSPENDED)로 이관
UPDATE users u
   SET account_status = 'SUSPENDED'
  FROM participants p
 WHERE p.user_id = u.id
   AND p.deleted_at IS NULL
   AND p.account_status = 'BLOCKED'
   AND (u.account_status IS NULL OR u.account_status = 'ACTIVE');

-- 2. applications: user 직결 컬럼 추가 및 백필 (비회원 신청은 NULL 유지)
ALTER TABLE applications ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id);

UPDATE applications a
   SET user_id = p.user_id
  FROM participants p
 WHERE a.participant_id = p.id
   AND a.user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_applications_user_id ON applications(user_id);

-- 3. ticket_passes: user 직결 컬럼 추가 및 백필
-- 레거시 비회원 이용권(있다면)은 user_id NULL로 남는다. 정리 완료 후 V5에서 NOT NULL 제약 예정.
ALTER TABLE ticket_passes ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id);

UPDATE ticket_passes tp
   SET user_id = p.user_id
  FROM participants p
 WHERE tp.participant_id = p.id
   AND tp.user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_passes_user_id ON ticket_passes(user_id);

-- 4. ticket_passes: 레거시 enum 상품(product) 표시명 백필 (D2 레거시 제거 후속)
UPDATE ticket_passes
   SET product_name = CASE product
                        WHEN 'RANDOM_TABLE_ONE'  THEN '우연한 식탁 1회권'
                        WHEN 'RANDOM_TABLE_FOUR' THEN '우연한 식탁 4회권'
                      END
 WHERE product_name IS NULL
   AND product IS NOT NULL;

-- 5. participant_id 컬럼의 NOT NULL 해제 (신규 insert는 participant를 채우지 않음)
ALTER TABLE ticket_passes ALTER COLUMN participant_id DROP NOT NULL;
