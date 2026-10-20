package com.whatsuphouse.backend.domain.form.enums;

/**
 * 우연한 식탁 표준 질문 키 (form_questions.reserved_key). 매칭 엔진이 이 키로 답변을 찾으므로
 * 이 키가 붙은 질문은 삭제·타입 변경이 막히고 라벨·선택지만 바꿀 수 있다.
 * RANDOM_TABLE 종류의 폼은 이 8개를 모두 가져야 한다. (KAN-341)
 */
public enum ReservedQuestionKey {
    BIRTH_YEAR,
    GENDER,
    MBTI,
    INTERESTS,
    MY_STYLE,
    WANTED_STYLE,
    DIET,
    // 식사 가격대(복수 선택). 매칭에는 쓰지 않고 식당 배정에서만 쓴다. (KAN-407)
    PRICE_RANGE
}
