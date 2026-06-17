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

-- 후기 좋아요 마일스톤 알림 중복 발행 방지용 컬럼 (KAN-263)
-- 마지막으로 발행한 마일스톤 값을 저장해, unlike→relike로 같은 마일스톤을 재교차해도 재발행하지 않는다.
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS notified_like_milestone INTEGER NOT NULL DEFAULT 0;
