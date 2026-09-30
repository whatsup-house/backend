-- V11: 매칭 해결 선택(match_resolutions) — 마지막 희망 회차에서도 못 앉은 신청의 대체 회차 이동·이용권 보관·환불,
--      테이블 멤버 제거 시각(dining_table_members.removed_at) (KAN-347)
-- 설계: docs/superpowers/specs/2026-09-29-dining-table-v2-design.md 2.4, 4.8
--
-- - applications·gathering_sessions·dining_table_members(V9)만 다루므로 V10(attendances, KAN-346)과 독립적이다.
-- - 같은 신청에 여러 행이 생길 수 있다(대체 회차로 옮긴 뒤 그 회차에서도 못 앉으면 새로 제안). 응답 대기(OFFERED)는 신청당 1건.

CREATE TABLE IF NOT EXISTS match_resolutions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID        NOT NULL REFERENCES applications(id),
    offered_session_ids JSONB       NOT NULL DEFAULT '[]'::jsonb,   -- 제안 회차 ID 목록. 비어 있으면 KEEP_TICKET/REFUND만 가능
    choice              VARCHAR(20),                                -- TRANSFER|KEEP_TICKET|REFUND, 응답 전 NULL
    chosen_session_id   UUID        REFERENCES gathering_sessions(id),
    respond_by          TIMESTAMP   NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OFFERED',     -- OFFERED|RESOLVED|EXPIRED
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_match_resolutions_application_id ON match_resolutions (application_id);
-- 만료 스케줄러: status = 'OFFERED' AND respond_by < now()
CREATE INDEX IF NOT EXISTS idx_match_resolutions_status_respond_by ON match_resolutions (status, respond_by);
CREATE UNIQUE INDEX IF NOT EXISTS uk_match_resolutions_offered_application
    ON match_resolutions (application_id) WHERE status = 'OFFERED';

-- ================================================
-- 테이블 멤버 제거 시각 (확정 후 취소 재조정)
-- ================================================
-- 취소한 멤버 행을 지우지 않고 removed_at만 채운다. 참석 기록(attendances.table_member_id, KAN-346)이
-- 이 행을 참조하고 취소를 CANCELED_EARLY로 남겨야 하기 때문이다. 인원·하드 조건·점수·멤버 조회·채팅 동기화와
-- "신청당 활성 테이블 1개" 판정은 removed_at IS NULL인 행만 센다. 수동 이동·분리·병합은 행을 옮긴다(삭제 아님).
ALTER TABLE dining_table_members ADD COLUMN IF NOT EXISTS removed_at TIMESTAMP NULL;
