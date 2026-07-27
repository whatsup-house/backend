package com.whatsuphouse.backend.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 세부 직업 사전. enum name이 user.job에 저장되는 코드다. 각 세부직업은 직업군(JobCategory)에 매핑된다. (KAN-271)
 * 신규 직업/캐릭터가 추가될 때 여기에 항목을 추가한다(확장 지점).
 */
@Getter
@RequiredArgsConstructor
public enum Job {

    // 학생
    UNIVERSITY_STUDENT("대학생", JobCategory.STUDENT),
    GRADUATE_STUDENT("대학원생", JobCategory.STUDENT),
    HIGH_SCHOOL_STUDENT("고등학생", JobCategory.STUDENT),
    EXCHANGE_STUDENT("교환학생", JobCategory.STUDENT),
    STUDENT_ETC("학생(기타)", JobCategory.STUDENT),

    // IT·개발
    SOFTWARE_DEVELOPER("개발자", JobCategory.IT_DEV),
    FRONTEND_DEVELOPER("프론트엔드 개발자", JobCategory.IT_DEV),
    BACKEND_DEVELOPER("백엔드 개발자", JobCategory.IT_DEV),
    DATA_ANALYST("데이터 분석가", JobCategory.IT_DEV),
    DATA_SCIENTIST("데이터 사이언티스트", JobCategory.IT_DEV),
    INFOSEC("정보보안", JobCategory.IT_DEV),
    PRODUCT_MANAGER("PM·서비스기획", JobCategory.IT_DEV),
    QA_ENGINEER("QA 엔지니어", JobCategory.IT_DEV),
    DEVOPS_ENGINEER("DevOps 엔지니어", JobCategory.IT_DEV),
    IT_ETC("IT·개발(기타)", JobCategory.IT_DEV),

    // 디자인·예술
    DESIGNER("디자이너", JobCategory.DESIGN_ART),
    UX_UI_DESIGNER("UX·UI 디자이너", JobCategory.DESIGN_ART),
    GRAPHIC_DESIGNER("그래픽 디자이너", JobCategory.DESIGN_ART),
    VIDEO_DESIGNER("영상 디자이너", JobCategory.DESIGN_ART),
    ILLUSTRATOR("일러스트레이터", JobCategory.DESIGN_ART),
    PHOTOGRAPHER("사진작가", JobCategory.DESIGN_ART),
    MUSICIAN("음악가", JobCategory.DESIGN_ART),
    VOCALIST("성악가", JobCategory.DESIGN_ART),
    WRITER("작가", JobCategory.DESIGN_ART),
    DESIGN_ART_ETC("디자인·예술(기타)", JobCategory.DESIGN_ART),

    // 미디어·방송
    PRODUCER("PD·프로듀서", JobCategory.MEDIA),
    LIVE_COMMERCE_PD("라이브커머스 PD", JobCategory.MEDIA),
    AD_PRODUCER("광고 PD", JobCategory.MEDIA),
    MEDIA_PRODUCTION("미디어 제작", JobCategory.MEDIA),
    BROADCAST_WRITER("방송작가", JobCategory.MEDIA),
    EDITOR("에디터", JobCategory.MEDIA),
    CONTENT_CREATOR("크리에이터", JobCategory.MEDIA),
    ANNOUNCER("아나운서", JobCategory.MEDIA),
    SPORTS_CASTER("스포츠 캐스터", JobCategory.MEDIA),
    MC_HOST("MC·사회자", JobCategory.MEDIA),
    MEDIA_ETC("미디어·방송(기타)", JobCategory.MEDIA),

    // 마케팅·광고
    MARKETER("마케터", JobCategory.MARKETING),
    PERFORMANCE_MARKETER("퍼포먼스 마케터", JobCategory.MARKETING),
    BRAND_MARKETER("브랜드 마케터", JobCategory.MARKETING),
    BEAUTY_MARKETER("뷰티 마케터", JobCategory.MARKETING),
    AD_PLANNER("광고기획(AE)", JobCategory.MARKETING),
    MERCHANDISER("MD", JobCategory.MARKETING),
    FASHION_MD("패션 MD", JobCategory.MARKETING),
    PR_SPECIALIST("홍보·PR", JobCategory.MARKETING),
    MARKETING_ETC("마케팅·광고(기타)", JobCategory.MARKETING),

    // 금융
    BANKER("은행원", JobCategory.FINANCE),
    SECOND_FINANCE("2금융", JobCategory.FINANCE),
    TAX_ACCOUNTANT("세무사", JobCategory.FINANCE),
    ACCOUNTANT("회계사", JobCategory.FINANCE),
    SECURITIES("증권·투자", JobCategory.FINANCE),
    INSURANCE("보험", JobCategory.FINANCE),
    FINANCE_OFFICE("재무·회계 사무", JobCategory.FINANCE),
    FINANCE_ETC("금융(기타)", JobCategory.FINANCE),

    // 법조
    LAWYER("변호사", JobCategory.LEGAL),
    JUDGE("판사", JobCategory.LEGAL),
    PROSECUTOR("검사", JobCategory.LEGAL),
    JUDICIAL_SCRIVENER("법무사", JobCategory.LEGAL),
    PATENT_ATTORNEY("변리사", JobCategory.LEGAL),
    LABOR_ATTORNEY("노무사", JobCategory.LEGAL),
    PARALEGAL("법무팀·사내변호사", JobCategory.LEGAL),
    LEGAL_ETC("법조(기타)", JobCategory.LEGAL),

    // 의료·보건
    NURSE("간호사", JobCategory.MEDICAL),
    DOCTOR("의사", JobCategory.MEDICAL),
    PHARMACIST("약사", JobCategory.MEDICAL),
    RADIOLOGIC_TECH("방사선사", JobCategory.MEDICAL),
    PHYSICAL_THERAPIST("물리치료사", JobCategory.MEDICAL),
    PHARMA_RESEARCHER("제약연구원", JobCategory.MEDICAL),
    DENTAL_COORDINATOR("치과코디네이터", JobCategory.MEDICAL),
    MEDICAL_TECH("임상병리사", JobCategory.MEDICAL),
    VETERINARIAN("수의사", JobCategory.MEDICAL),
    MEDICAL_ETC("의료·보건(기타)", JobCategory.MEDICAL),

    // 교육
    TEACHER("교사", JobCategory.EDUCATION),
    KINDERGARTEN_TEACHER("유치원·어린이집 교사", JobCategory.EDUCATION),
    LECTURER("강사", JobCategory.EDUCATION),
    PROFESSOR("교수", JobCategory.EDUCATION),
    ACADEMY_INSTRUCTOR("학원강사", JobCategory.EDUCATION),
    EDUCATION_ETC("교육(기타)", JobCategory.EDUCATION),

    // 공무원·공공
    CIVIL_SERVANT("공무원", JobCategory.PUBLIC),
    POLICE_OFFICER("경찰공무원", JobCategory.PUBLIC),
    LOCAL_GOV_OFFICER("지자체 공무원", JobCategory.PUBLIC),
    PUBLIC_CORP("공기업", JobCategory.PUBLIC),
    SOLDIER("군인", JobCategory.PUBLIC),
    FIREFIGHTER("소방관", JobCategory.PUBLIC),
    SOCIAL_WORKER("사회복지사", JobCategory.PUBLIC),
    PUBLIC_ETC("공공(기타)", JobCategory.PUBLIC),

    // 서비스
    HOTELIER("호텔리어", JobCategory.SERVICE),
    BARISTA("바리스타", JobCategory.SERVICE),
    BAKER("제빵사", JobCategory.SERVICE),
    CHEF("요리사", JobCategory.SERVICE),
    SECRETARY("비서", JobCategory.SERVICE),
    SALES("영업직", JobCategory.SERVICE),
    CALL_CENTER("콜센터 상담사", JobCategory.SERVICE),
    RETAIL_STAFF("매장 근무", JobCategory.SERVICE),
    HAIR_DESIGNER("헤어디자이너", JobCategory.SERVICE),
    BEAUTY_WORKER("미용인", JobCategory.SERVICE),
    FLIGHT_ATTENDANT("승무원", JobCategory.SERVICE),
    REALTOR("공인중개사", JobCategory.SERVICE),
    SERVICE_ETC("서비스(기타)", JobCategory.SERVICE),

    // 제조·엔지니어링
    ENGINEER("엔지니어", JobCategory.MANUFACTURING),
    SEMICONDUCTOR_ENGINEER("반도체 엔지니어", JobCategory.MANUFACTURING),
    MECHANICAL_ENGINEER("기계 엔지니어", JobCategory.MANUFACTURING),
    ELECTRICAL_ENGINEER("전기 엔지니어", JobCategory.MANUFACTURING),
    PROCESS_OPERATOR("공정 오퍼레이터", JobCategory.MANUFACTURING),
    PRODUCTION_WORKER("생산직", JobCategory.MANUFACTURING),
    QUALITY_CONTROL("품질관리", JobCategory.MANUFACTURING),
    ARCHITECT("건축사", JobCategory.MANUFACTURING),
    MANUFACTURING_ETC("제조·엔지니어링(기타)", JobCategory.MANUFACTURING),

    // 일반 사무·직장인
    OFFICE_WORKER("회사원", JobCategory.OFFICE),
    OFFICE_CLERK("사무직", JobCategory.OFFICE),
    FOREIGN_COMPANY("외국계 회사원", JobCategory.OFFICE),
    LARGE_COMPANY("대기업 사무직", JobCategory.OFFICE),
    HR("인사·HR", JobCategory.OFFICE),
    GENERAL_AFFAIRS("총무", JobCategory.OFFICE),
    OFFICE_ETC("일반 사무(기타)", JobCategory.OFFICE),

    // 자영업·창업·프리랜서
    STARTUP_FOUNDER("스타트업 대표", JobCategory.SELF_EMPLOYED),
    SELF_EMPLOYED_OWNER("자영업·사장", JobCategory.SELF_EMPLOYED),
    FREELANCER("프리랜서", JobCategory.SELF_EMPLOYED),
    ENTREPRENEUR("창업가", JobCategory.SELF_EMPLOYED),
    SELF_EMPLOYED_ETC("자영업·창업(기타)", JobCategory.SELF_EMPLOYED),

    // 기타
    JOB_SEEKER("취업준비생", JobCategory.ETC),
    UNEMPLOYED("무직·휴식중", JobCategory.ETC),
    ETC("기타", JobCategory.ETC);

    private final String label;
    private final JobCategory category;

    private static final Map<String, Job> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));

    public String getCode() {
        return name();
    }

    public static Optional<Job> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /** null/blank는 허용(미입력), 값이 있으면 사전에 존재해야 한다. */
    public static boolean isAcceptable(String code) {
        return code == null || code.isBlank() || BY_CODE.containsKey(code);
    }
}
