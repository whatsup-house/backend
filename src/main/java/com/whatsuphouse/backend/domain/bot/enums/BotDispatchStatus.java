package com.whatsuphouse.backend.domain.bot.enums;

/**
 * 대기 문의 상태(KAN-406). PENDING(디바운스·조회 대기) → LEASED(에이전트가 조회, leased_until까지 답변 유효)
 * → REPLIED | HANDED_OFF | FAILED(lease 3회 만료).
 */
public enum BotDispatchStatus {
    PENDING, LEASED, REPLIED, HANDED_OFF, FAILED
}
