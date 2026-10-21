-- V33: 신청 상태 변경 이력(PRD 16장)과 우연한 식탁 안내 발송 이력(INF-08). (KAN-437)
-- 참조 컬럼은 FK 없음(exception_cases와 같다). 취소·삭제된 신청·해체된 테이블의 이력도 남긴다.

-- 항목별 직전 값(from_value)과 바뀐 값(to_value). 그 항목의 첫 기록은 from_value NULL.
-- 값: STATUS = applications.status, MATCH_STATUS = applications.match_status, PAYMENT = 통합 결제 상태(계산값)
CREATE TABLE IF NOT EXISTS application_status_logs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID         NOT NULL,
    field          VARCHAR(20)  NOT NULL,   -- STATUS|MATCH_STATUS|PAYMENT
    from_value     VARCHAR(30),
    to_value       VARCHAR(30),
    actor_id       UUID,                    -- NULL = 시스템(스케줄러·웹훅)
    reason         VARCHAR(200),
    created_at     TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_application_status_logs_application
    ON application_status_logs (application_id, created_at DESC);

-- 발송 시도 1건 = 1행. attempt = 같은 테이블·종류·채널의 몇 번째 시도인지(1부터).
CREATE TABLE IF NOT EXISTS notice_logs (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID         NOT NULL,
    table_id   UUID,
    kind       VARCHAR(30)  NOT NULL,   -- CONFIRM_MESSAGE|REMINDER|CONTENTS|NOTIFICATION
    channel    VARCHAR(20)  NOT NULL,   -- CHAT|IN_APP|PUSH
    status     VARCHAR(20)  NOT NULL,   -- SUCCESS|FAILED
    attempt    INTEGER      NOT NULL,
    error      VARCHAR(300),
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notice_logs_session ON notice_logs (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notice_logs_table_kind ON notice_logs (table_id, kind, created_at DESC);
