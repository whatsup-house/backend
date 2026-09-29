package com.whatsuphouse.backend.domain.chat.enums;

/** 시스템 메시지 종류. 문구는 FE가 system_kind + system_params로 렌더한다. */
public enum ChatSystemKind {
    JOINED, KICKED, NOTICE_SET
}
