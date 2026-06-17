-- 인앱 알림 테이블 (KAN-263)
-- 참가 확정 / 마일리지 적립 / 후기 좋아요 마일스톤 등 이벤트를 사용자별 알림으로 적재한다.
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
