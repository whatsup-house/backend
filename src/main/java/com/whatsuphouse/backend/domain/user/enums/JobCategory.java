package com.whatsuphouse.backend.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 직업군(대분류). 각 직업군은 캐릭터 PNG 한 장에 매핑된다. (KAN-271)
 * 신청서 도메인에 있던 미사용 JobCategory를 회원 직업 체계로 통합하며 MEDIA, OFFICE를 추가했다.
 */
@Getter
@RequiredArgsConstructor
public enum JobCategory {

    STUDENT("학생"),
    IT_DEV("IT·개발"),
    DESIGN_ART("디자인·예술"),
    MEDIA("미디어·방송"),
    MARKETING("마케팅·광고"),
    FINANCE("금융"),
    MEDICAL("의료·보건"),
    EDUCATION("교육"),
    PUBLIC("공무원·공공"),
    SERVICE("서비스"),
    MANUFACTURING("제조·엔지니어링"),
    OFFICE("일반 사무·직장인"),
    SELF_EMPLOYED("자영업·창업·프리랜서"),
    ETC("기타");

    private final String label;
}
