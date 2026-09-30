package com.whatsuphouse.backend.domain.matching.enums;

/** 매칭 실패 신청자의 해결 선택. TRANSFER=대체 회차로 이동, KEEP_TICKET=이용권 보관(복원), REFUND=환불. (설계 4.8) */
public enum ResolutionChoice {
    TRANSFER,
    KEEP_TICKET,
    REFUND
}
