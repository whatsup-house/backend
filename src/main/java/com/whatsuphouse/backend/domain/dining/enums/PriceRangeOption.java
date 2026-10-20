package com.whatsuphouse.backend.domain.dining.enums;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 표준 질문 PRICE_RANGE의 선택지(V24 시드와 같은 문구). 식당 배정에서 Venue.priceRange(자유 문자열)를 이 선택지로 분류한다. (KAN-407)
 */
public enum PriceRangeOption {
    LOW("10,000~20,000원"),
    MID("20,000~30,000원"),
    HIGH("30,000원 이상 (상관 없음)");

    private static final long MIN_AMOUNT = 1_000;
    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]{0,11})\\s*(만)?");

    private final String label;

    PriceRangeOption(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 식당 가격대 문자열에서 금액(콤마 허용, "만" 단위 허용, 1,000원 미만 숫자는 인원수로 보고 무시)을 모아 평균으로 선택지를 고른다.
     * 20,000원 미만 LOW, 30,000원 미만 MID, 그 이상 HIGH. 숫자가 없으면 empty(선호 없음으로 본다).
     * ponytail: "1만5천" 같은 복합 표기는 지원하지 않는다. 필요해지면 식당에 정형 가격대 컬럼을 둔다.
     */
    public static Optional<PriceRangeOption> ofVenueText(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = AMOUNT.matcher(text);
        long sum = 0;
        int count = 0;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1).replace(",", ""));
            amount = matcher.group(2) != null ? amount * 10_000 : amount;
            if (amount >= MIN_AMOUNT) { // "1인", "2인 이상" 같은 인원 숫자는 금액이 아니다
                sum += amount;
                count++;
            }
        }
        if (count == 0) {
            return Optional.empty();
        }
        long average = sum / count;
        return Optional.of(average < 20_000 ? LOW : average < 30_000 ? MID : HIGH);
    }
}
