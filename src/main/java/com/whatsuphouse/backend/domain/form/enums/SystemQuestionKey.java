package com.whatsuphouse.backend.domain.form.enums;

import lombok.Getter;

/**
 * 시스템 예약 질문 키. 신청 로직(비회원 이름/연락처/이메일 추출)이 이 키에 의존하므로
 * 문자열 리터럴 대신 반드시 이 enum을 사용한다. 폼 수정 시 예약 질문 키는 변경 금지.
 */
@Getter
public enum SystemQuestionKey {
    NAME("name"),
    PHONE("phone"),
    EMAIL("email");

    private final String key;

    SystemQuestionKey(String key) {
        this.key = key;
    }
}
