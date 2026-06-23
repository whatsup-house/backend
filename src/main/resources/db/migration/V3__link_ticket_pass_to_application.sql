-- 이용권 구매 요청이 특정 우연한 식탁 신청에서 발생한 경우 해당 신청과 직접 연결한다. (KAN-289)
ALTER TABLE ticket_passes
    ADD COLUMN IF NOT EXISTS application_id UUID REFERENCES applications(id);

CREATE INDEX IF NOT EXISTS idx_ticket_passes_application_id ON ticket_passes(application_id);
