-- V25: 그록봇 연동을 웹훅 푸시 → Slack 신호 + 대기 문의 조회(lease)로 전환. (KAN-406)
-- 상태: PENDING → LEASED(에이전트가 조회, leased_until까지 답변 유효) → REPLIED | HANDED_OFF | FAILED.
-- SENT는 더 이상 쓰지 않는다(V23 미배포라 기존 행 없음). status엔 CHECK 제약이 없고,
-- 방당 대기 1건 부분 유니크(uk_bot_dispatch_pending_conversation, status = 'PENDING')는 그대로 유지한다.

ALTER TABLE bot_dispatch ADD COLUMN IF NOT EXISTS leased_until TIMESTAMP;

-- 웹훅 재시도·발송 시각·발송 오류 컬럼은 쓰지 않는다.
ALTER TABLE bot_dispatch
    DROP COLUMN IF EXISTS next_attempt_at,
    DROP COLUMN IF EXISTS sent_at,
    DROP COLUMN IF EXISTS last_error;

-- lease 만료 스캔
CREATE INDEX IF NOT EXISTS idx_bot_dispatch_status_leased_until ON bot_dispatch(status, leased_until);
