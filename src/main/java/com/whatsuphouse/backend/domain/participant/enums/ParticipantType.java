package com.whatsuphouse.backend.domain.participant.enums;

public enum ParticipantType {
    MEMBER, // 로그인 회원 참가자 (user_id 연결)
    GUEST   // 비회원 참가자 (user_id NULL)
}
