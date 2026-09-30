-- V8: 이용권 차감 레코드 상태 (KAN-342)
--
-- ticket_transactions.status: 차감(USE) 거래의 상태. 값은 TicketDeductionStatus enum 이름.
--   DEDUCTED | RESTORED | REFUND_REQUESTED | REFUND_PROCESSING | REFUNDED | REFUND_FAILED
-- 차감이 아닌 거래(ISSUE/REFUND/ADMIN_ADD/ADMIN_DEDUCT/EXPIRE)는 NULL.
-- 기존 USE 행은 DEDUCTED로 백필한다. 같은 신청에 REFUND 거래가 이미 있는 USE 행은 취소로 복원된 것이므로 RESTORED.
-- 다른 테이블을 새로 참조하지 않으므로 V7 없이도 단독 적용된다.

ALTER TABLE ticket_transactions ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE ticket_transactions u
   SET status = CASE
           WHEN u.application_id IS NOT NULL AND EXISTS (
                SELECT 1
                  FROM ticket_transactions r
                 WHERE r.application_id = u.application_id
                   AND r.transaction_type = 'REFUND')
           THEN 'RESTORED'
           ELSE 'DEDUCTED'
       END
 WHERE u.transaction_type = 'USE'
   AND u.status IS NULL;
