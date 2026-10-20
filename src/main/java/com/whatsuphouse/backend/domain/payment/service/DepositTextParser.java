package com.whatsuphouse.backend.domain.payment.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 은행 입금 알림 원문 → (입금자명, 금액). 정규식을 순서대로 시도한다. (설계 14, KAN-403) */
public final class DepositTextParser {

    public record ParsedDeposit(String name, int amount) {
    }

    // 우리WON뱅킹 입출금 푸시. 예) [입금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30
    private static final Pattern WOORI_PUSH = Pattern.compile("^\\[입금\\]\\s+(.+?)\\s+([\\d,]+)원\\s");
    // ponytail: 은행 문자(아이폰 단축어) 정규식은 문자 원문 샘플을 받으면 여기에 추가한다. 그전까지 문자는 PARSE_FAILED로 예외함에 간다.
    private static final List<Pattern> PATTERNS = List.of(WOORI_PUSH);
    // 회원 실명 컬럼(users.name) 길이. 이보다 길면 이름이 아니다.
    private static final int MAX_NAME_LENGTH = 50;

    private DepositTextParser() {
    }

    /**
     * 입금 알림인가. 출금 등은 false(기록만 하고 무시). 형식을 못 읽어도 입금 알림이면 true라
     * 호출부가 IGNORED와 PARSE_FAILED를 구분한다. 문자 원문은 "[입금]"으로 시작하지 않으므로 "입금" 포함으로 본다.
     */
    public static boolean isDepositAlert(String raw) {
        return raw != null && raw.contains("입금");
    }

    public static Optional<ParsedDeposit> parse(String raw) {
        if (!isDepositAlert(raw)) {
            return Optional.empty();
        }
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(raw);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (name.length() > MAX_NAME_LENGTH) {
                    return Optional.empty();
                }
                try {
                    int amount = Integer.parseInt(matcher.group(2).replace(",", ""));
                    return Optional.of(new ParsedDeposit(name, amount));
                } catch (NumberFormatException e) {
                    return Optional.empty();    // 쉼표만 있거나 int 범위 초과
                }
            }
        }
        return Optional.empty();
    }
}
