-- V23: 1:1 문의방 그록봇 AI 자동응답 — 웹훅 발송 큐·전역 토글·봇 계정·채팅 플래그. (KAN-406)

-- 고객 메시지 1건(디바운스로 묶인 마지막 메시지)당 웹훅 발송 1건.
-- last_error에는 상태 코드·예외 클래스만 남긴다(비밀값·요청 본문 금지).
CREATE TABLE IF NOT EXISTS bot_dispatch (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL UNIQUE,
    conversation_id UUID        NOT NULL REFERENCES chat_rooms(id),
    message_id      UUID        NOT NULL UNIQUE REFERENCES chat_messages(id),
    customer_id     UUID        NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL,
    attempts        INT         NOT NULL DEFAULT 0,
    last_error      TEXT,
    send_after      TIMESTAMP   NOT NULL,
    next_attempt_at TIMESTAMP,
    sent_at         TIMESTAMP,
    handoff_reason  TEXT,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bot_dispatch_status_send_after ON bot_dispatch(status, send_after);
CREATE INDEX IF NOT EXISTS idx_bot_dispatch_conversation_created ON bot_dispatch(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_bot_dispatch_customer_created ON bot_dispatch(customer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_bot_dispatch_created ON bot_dispatch(created_at);
-- 방당 발송 대기(PENDING)는 최대 1건(디바운스 대상). 앱은 단일 스레드로 넣지만 DB에서도 막는다.
CREATE UNIQUE INDEX IF NOT EXISTS uk_bot_dispatch_pending_conversation
    ON bot_dispatch(conversation_id) WHERE status = 'PENDING';

-- 관리자 화면 전역 토글. 1행 고정.
CREATE TABLE IF NOT EXISTS bot_settings (
    id         SMALLINT  PRIMARY KEY CHECK (id = 1),
    enabled    BOOLEAN   NOT NULL DEFAULT true,
    updated_by UUID      REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO bot_settings (id, enabled, updated_at) VALUES (1, true, now()) ON CONFLICT (id) DO NOTHING;

ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS needs_human BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS bot_paused BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS ai_generated BOOLEAN NOT NULL DEFAULT false;

-- 봇 전용 시스템 계정(BotAccount.ID). 관리자 아님. 비밀번호는 버린 랜덤 값의 bcrypt 해시라 로그인할 수 없다.
INSERT INTO users (id, email, password, name, gender, age, nickname, is_admin, mileage_balance, account_status,
                   created_at, updated_at)
VALUES ('00000000-0000-0000-0000-00000000b07a', 'bot@whatsup.house',
        '$2a$10$XqSb3TR/.s7T36PRTcruC.7T.i2gDURT8PKJtoLi52mB3tp.8roTi',
        '와썹 AI', 'MALE', 0, '와썹 AI', false, 0, 'ACTIVE', now(), now())
ON CONFLICT DO NOTHING;
