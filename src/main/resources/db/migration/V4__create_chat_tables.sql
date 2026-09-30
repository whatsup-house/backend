-- V4: 채팅 (KAN-328, docs/chat-design.md 1절)
-- 본문(content_enc)은 서버측 AES-256-GCM 암호문, nonce는 메시지마다 12바이트 랜덤. SYSTEM 메시지는 둘 다 NULL.
-- source_id는 GATHERING/MATCHING_GROUP 표시용 다형 참조라 FK를 두지 않는다.

CREATE TABLE IF NOT EXISTS chat_rooms (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type              VARCHAR(10)  NOT NULL,
    name              VARCHAR(100),
    source_type       VARCHAR(20),
    source_id         UUID,
    inquiry_user_id   UUID         UNIQUE REFERENCES users(id),
    notice_message_id UUID,
    created_by        UUID         NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    deleted_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id       UUID         NOT NULL REFERENCES chat_rooms(id),
    sender_id     UUID         REFERENCES users(id),
    type          VARCHAR(10)  NOT NULL,
    content_enc   BYTEA,
    nonce         BYTEA,
    system_kind   VARCHAR(30),
    system_params JSONB,
    link_preview  JSONB,
    deleted_at    TIMESTAMP,
    edited_at     TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created ON chat_messages(room_id, created_at);

ALTER TABLE chat_rooms
    ADD CONSTRAINT fk_chat_rooms_notice_message FOREIGN KEY (notice_message_id) REFERENCES chat_messages(id);

CREATE TABLE IF NOT EXISTS chat_members (
    id                   UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id              UUID      NOT NULL REFERENCES chat_rooms(id),
    user_id              UUID      NOT NULL REFERENCES users(id),
    joined_at            TIMESTAMP NOT NULL,
    left_at              TIMESTAMP,
    last_read_message_id UUID      REFERENCES chat_messages(id),
    hidden               BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_chat_members_room_user UNIQUE (room_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_members_user_id ON chat_members(user_id);

CREATE TABLE IF NOT EXISTS chat_reactions (
    message_id UUID       NOT NULL REFERENCES chat_messages(id),
    user_id    UUID       NOT NULL REFERENCES users(id),
    emoji      VARCHAR(8) NOT NULL,
    PRIMARY KEY (message_id, user_id, emoji)
);

CREATE TABLE IF NOT EXISTS chat_mutes (
    user_id    UUID      PRIMARY KEY REFERENCES users(id),
    muted_by   UUID      NOT NULL REFERENCES users(id),
    reason     TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_reports (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id  UUID        NOT NULL REFERENCES chat_messages(id),
    reporter_id UUID        NOT NULL REFERENCES users(id),
    reason      TEXT        NOT NULL,
    status      VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_reports_status ON chat_reports(status);

-- 웹 푸시 구독 (발송은 별도 일감. 이번엔 테이블만)
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL REFERENCES users(id),
    endpoint   TEXT      NOT NULL UNIQUE,
    p256dh     TEXT      NOT NULL,
    auth       TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_push_subscriptions_user_id ON push_subscriptions(user_id);
