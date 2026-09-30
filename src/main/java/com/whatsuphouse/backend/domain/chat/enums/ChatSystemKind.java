package com.whatsuphouse.backend.domain.chat.enums;

/** 시스템 메시지 종류. 문구는 FE가 system_kind + system_params로 렌더한다. */
public enum ChatSystemKind {
    JOINED, KICKED, NOTICE_SET,
    // 서버가 남기는 안내문(우연한 식탁 확정 안내 등). system_params = {text}
    SYSTEM_NOTICE
}
