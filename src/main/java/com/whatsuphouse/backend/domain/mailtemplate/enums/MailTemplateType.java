package com.whatsuphouse.backend.domain.mailtemplate.enums;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 시스템 알림 메일 종류. 각 타입은 기본 제목/본문(하드코딩 폴백)과 사용 가능한 치환 변수를 가진다.
 * 관리자가 운영 중 수정한 내용은 mail_templates 테이블에 저장되어 기본값을 오버라이드한다. (KAN-244)
 * 변수는 본문에서 {{변수명}} 형태로 사용하며, 관리자가 알아보기 쉽도록 한글 변수명을 쓴다.
 */
@Getter
public enum MailTemplateType {

    WELCOME(
            "회원가입 환영",
            "[Whats up House] 가입을 환영합니다!",
            """
            안녕하세요, {{닉네임}}님!

            Whats up House에 가입해 주셔서 감사합니다.
            가입 축하 마일리지 1,000P가 적립되었습니다.

            다양한 모임에서 새로운 사람들을 만나보세요.
            """,
            List.of("닉네임"),
            Map.of("닉네임", "홍길동")
    ),

    PASSWORD_RESET(
            "비밀번호 재설정",
            "[Whats up House] 비밀번호 재설정 안내",
            """
            안녕하세요, {{닉네임}}님!

            아래 링크에서 비밀번호를 재설정해 주세요.
            링크는 30분 동안 1회만 사용할 수 있습니다.

            {{재설정링크}}

            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """,
            List.of("닉네임", "재설정링크"),
            Map.of("닉네임", "홍길동", "재설정링크", "https://www.whatsup.house/password-reset/confirm?token=sample-token")
    ),

    APPLICATION_PENDING(
            "신청 접수",
            "[Whats up House] 신청이 접수되었습니다 - {{모임명}}",
            """
            안녕하세요, {{이름}}님!

            모임 신청이 정상적으로 접수되었습니다.

            모임명: {{모임명}}
            일시: {{모임날짜}} {{시작시간}}
            예약번호: {{예약번호}}

            신청 결과는 별도 이메일로 안내해 드리겠습니다.
            """,
            List.of("이름", "모임명", "모임날짜", "시작시간", "예약번호"),
            Map.of("이름", "홍길동", "모임명", "재즈가 흐르는 와인 모임",
                    "모임날짜", "2026년 06월 26일", "시작시간", "19:00", "예약번호", "WH260626-AB12CD")
    ),

    APPLICATION_CONFIRMED(
            "신청 확정 (입금 안내)",
            "[Whats up House] 신청이 확정되었습니다 - {{모임명}}",
            """
            안녕하세요, {{이름}}님!

            모임 참가가 확정되었습니다. 아래 계좌로 입금해 주시면 참석이 최종 확정됩니다.

            모임명: {{모임명}}
            일시: {{모임날짜}} {{시작시간}}
            예약번호: {{예약번호}}

            [입금 안내]
            입금 계좌: 국민은행 000000-00-000000 (예금주: 와썹하우스)
            입금 금액: {{입금금액}}원

            입금이 확인되면 별도 안내 메일을 보내드립니다.
            """,
            List.of("이름", "모임명", "모임날짜", "시작시간", "예약번호", "입금금액"),
            Map.of("이름", "홍길동", "모임명", "재즈가 흐르는 와인 모임",
                    "모임날짜", "2026년 06월 26일", "시작시간", "19:00", "예약번호", "WH260626-AB12CD",
                    "입금금액", "25,000")
    ),

    APPLICATION_CANCELLED(
            "신청 취소",
            "[Whats up House] 신청이 취소되었습니다 - {{모임명}}",
            """
            안녕하세요, {{이름}}님!

            아래 신청이 취소 처리되었습니다.

            모임명: {{모임명}}
            예약번호: {{예약번호}}

            다음 모임에서 만나뵐 수 있기를 기대합니다.
            """,
            List.of("이름", "모임명", "예약번호"),
            Map.of("이름", "홍길동", "모임명", "재즈가 흐르는 와인 모임", "예약번호", "WH260626-AB12CD")
    ),

    APPLICATION_ATTENDED(
            "참석 확인 / 마일리지 적립",
            "[Whats up House] 참석이 확인되었습니다 - {{모임명}}",
            """
            안녕하세요, {{이름}}님!

            모임 참석이 확인되어 마일리지가 적립되었습니다.

            모임명: {{모임명}}
            적립 마일리지: +{{적립마일리지}}P
            현재 잔액: {{마일리지잔액}}P
            """,
            List.of("이름", "모임명", "적립마일리지", "마일리지잔액"),
            Map.of("이름", "홍길동", "모임명", "재즈가 흐르는 와인 모임",
                    "적립마일리지", "1,000", "마일리지잔액", "2,000")
    ),

    GATHERING_CANCELLED(
            "모임 전체 취소",
            "[Whats up House] 모임이 취소되었습니다 - {{모임명}}",
            """
            안녕하세요, {{이름}}님!

            신청하셨던 모임이 취소되었습니다.

            모임명: {{모임명}}
            예약번호: {{예약번호}}

            불편을 드려 죄송합니다. 다른 모임도 많이 확인해 보세요.
            """,
            List.of("이름", "모임명", "예약번호"),
            Map.of("이름", "홍길동", "모임명", "재즈가 흐르는 와인 모임", "예약번호", "WH260626-AB12CD")
    ),

    PAYMENT_CONFIRMED(
            "입금 완료 안내",
            "[Whats up House] 입금이 확인되었습니다 - {{모임명}}",
            """
            안녕하세요, {{이름}}님!

            입금이 확인되어 참석이 최종 확정되었습니다.

            모임명: {{모임명}}
            예약번호: {{예약번호}}

            당일 즐거운 시간 보내세요!
            """,
            List.of("이름", "모임명", "예약번호"),
            Map.of("이름", "홍길동", "모임명", "재즈가 흐르는 와인 모임", "예약번호", "WH260626-AB12CD")
    );

    private final String description;
    private final String defaultSubject;
    private final String defaultBody;
    private final List<String> variables;
    private final Map<String, String> sampleVariables;

    MailTemplateType(String description, String defaultSubject, String defaultBody,
                     List<String> variables, Map<String, String> sampleVariables) {
        this.description = description;
        this.defaultSubject = defaultSubject;
        this.defaultBody = defaultBody;
        this.variables = variables;
        this.sampleVariables = sampleVariables;
    }

    public static MailTemplateType fromKey(String key) {
        try {
            return MailTemplateType.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
