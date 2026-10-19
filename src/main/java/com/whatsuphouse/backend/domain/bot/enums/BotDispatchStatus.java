package com.whatsuphouse.backend.domain.bot.enums;

/** 웹훅 발송 상태. PENDING은 디바운스·재시도 대기 포함. (KAN-406) */
public enum BotDispatchStatus {
    PENDING, SENT, REPLIED, HANDED_OFF, FAILED
}
