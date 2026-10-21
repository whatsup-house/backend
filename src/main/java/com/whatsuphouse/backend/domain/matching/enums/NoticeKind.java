package com.whatsuphouse.backend.domain.matching.enums;

/** 안내 발송 이력 종류. (KAN-437) */
public enum NoticeKind {
    CONFIRM_MESSAGE,    // 확정 단체방 개설 + 운영자 안내 메시지
    REMINDER,           // 회차 전날 리마인드
    CONTENTS,           // 대화 주제·아이스브레이킹 게시
    NOTIFICATION        // 확정 인앱 알림
}
