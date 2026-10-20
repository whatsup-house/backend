-- V22: 운영자 송금 요청(환불·보증금 반환). 운영자가 실제 이체 후 SENT로 바꾼다. (KAN-404)
-- 설계: docs/superpowers/specs/2026-10-19-dining-hackathon-gaps-design.md 3.3, 4.3
-- 계좌는 이 건에만 저장하고 송금 30일 후 끝 4자리만 남긴다(account_masked).

CREATE TABLE IF NOT EXISTS payout_requests (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users(id),
    type           VARCHAR(20) NOT NULL,   -- REFUND|DEPOSIT_RETURN
    amount         INTEGER     NOT NULL,
    bank           VARCHAR(50),
    account_number VARCHAR(50),
    holder         VARCHAR(50),
    account_masked BOOLEAN     NOT NULL DEFAULT FALSE,
    status         VARCHAR(20) NOT NULL,   -- REQUESTED|SENT
    application_id UUID        REFERENCES applications(id),
    ticket_pass_id UUID        REFERENCES ticket_passes(id),
    deposit_id     UUID        REFERENCES deposits(id),
    sent_at        TIMESTAMP,
    sent_by        UUID,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payout_requests_type_status ON payout_requests (type, status, created_at);
CREATE INDEX IF NOT EXISTS idx_payout_requests_ticket_pass ON payout_requests (ticket_pass_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_sent_at ON payout_requests (sent_at) WHERE account_masked = FALSE;

-- 중복 송금 방지 최후 방어선: 보증금 1건당 반환 1건, 신청 1건당 환불 1건, 멤버십 1건당 환불 1건.
CREATE UNIQUE INDEX IF NOT EXISTS uk_payout_requests_deposit
    ON payout_requests (deposit_id) WHERE deposit_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payout_requests_refund_application
    ON payout_requests (application_id) WHERE type = 'REFUND' AND application_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payout_requests_refund_membership
    ON payout_requests (ticket_pass_id) WHERE type = 'REFUND' AND application_id IS NULL;
