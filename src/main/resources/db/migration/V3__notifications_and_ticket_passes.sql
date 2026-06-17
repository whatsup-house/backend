-- 인앱 알림(KAN-263) + 우연한 식탁 이용권(KAN-261) 신규 테이블 통합 마이그레이션
-- 두 기능이 각자 다른 PR이지만, 신규 테이블은 단일 V3로 합쳐 관리한다.
-- (두 PR이 동일한 이 파일을 동일하게 포함하므로 순차 머지 시 동일 내용 add/add로 충돌 없이 합쳐진다.)

-- ── 인앱 알림 (KAN-263) ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id),
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    link        VARCHAR(40),
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    deleted_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read);

-- 후기 좋아요 마일스톤 알림 중복 발행 방지용 컬럼 (KAN-263)
-- 마지막으로 발행한 마일스톤 값을 저장해, unlike→relike로 같은 마일스톤을 재교차해도 재발행하지 않는다.
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS notified_like_milestone INTEGER NOT NULL DEFAULT 0;

-- ── 우연한 식탁 이용권 (KAN-261) ─────────────────────────────────────
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
