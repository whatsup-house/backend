-- V20: 노쇼 보증금. 보증금 대상 회원의 우연한 식탁 신청 1건당 1행. (KAN-402)
-- 설계: docs/superpowers/specs/2026-10-19-dining-hackathon-gaps-design.md 4.3

CREATE TABLE IF NOT EXISTS deposits (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES applications(id),
    amount         INTEGER     NOT NULL,
    status         VARCHAR(20) NOT NULL,   -- PENDING|PAID|RETURN_PENDING|RETURNED|FORFEITED
    return_bank    VARCHAR(50),
    return_account VARCHAR(50),
    return_holder  VARCHAR(50),
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL,
    CONSTRAINT uk_deposits_application UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_deposits_status ON deposits (status);
