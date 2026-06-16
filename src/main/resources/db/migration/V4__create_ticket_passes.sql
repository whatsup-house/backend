-- 우연한 식탁 이용권(선결제 회차권) 테이블 (KAN-261)
-- 구매 시 PENDING(잔여 0)으로 생성되고, 관리자가 입금 확인하면 ACTIVE로 활성화되며 잔여가 충전된다.
CREATE TABLE IF NOT EXISTS ticket_passes (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    product         VARCHAR(40)  NOT NULL,
    total_count     INTEGER      NOT NULL,
    remaining_count INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL,
    activated_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    deleted_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ticket_passes_user_id ON ticket_passes(user_id);
CREATE INDEX IF NOT EXISTS idx_ticket_passes_status ON ticket_passes(status);
