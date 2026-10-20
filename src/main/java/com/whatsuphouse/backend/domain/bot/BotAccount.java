package com.whatsuphouse.backend.domain.bot;

import java.util.UUID;

/** 그록봇 전용 시스템 계정(V23이 users에 넣는다). 관리자 아님, 로그인 불가. 봇 답변 메시지의 발신자. (KAN-406) */
public final class BotAccount {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000b07a");

    private BotAccount() {
    }
}
