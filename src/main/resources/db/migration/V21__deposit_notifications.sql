-- V21: 입금 알림(은행 푸시·문자 원문) 기록과 자동 대사 결과. (KAN-403)
-- 설계: docs/superpowers/specs/2026-10-19-dining-hackathon-gaps-design.md 2, 14
-- text_hash = 원문 sha256. MacroDroid 재시도 등 같은 원문 중복 수신을 막는다.

CREATE TABLE IF NOT EXISTS deposit_notifications (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_text          TEXT         NOT NULL,
    text_hash         VARCHAR(64)  NOT NULL,
    source            VARCHAR(20)  NOT NULL,   -- MACRODROID|IOS_SHORTCUT|SIMULATOR
    received_at       TIMESTAMP    NOT NULL,
    parsed_name       VARCHAR(100),
    parsed_amount     INTEGER,
    result            VARCHAR(30)  NOT NULL,   -- MATCHED|IGNORED|NO_CANDIDATE|MULTIPLE_CANDIDATES|PARSE_FAILED
    target_type       VARCHAR(30),             -- TICKET_PASS|DEPOSIT|REGULAR_APPLICATION
    target_id         UUID,
    application_id    UUID,
    exception_case_id UUID,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_deposit_notifications_text_hash UNIQUE (text_hash)
);

CREATE INDEX IF NOT EXISTS idx_deposit_notifications_created_at ON deposit_notifications (created_at DESC);
