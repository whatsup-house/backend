package com.whatsuphouse.backend.domain.dining.enums;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 우연한 식탁 지역(고정 4곳). API·DB에는 라벨 문자열을 그대로 쓴다(venues.region, dining_tables.region이 텍스트라서).
 * 선언 순서가 표시·조회 순서다. (KAN-430)
 */
public enum DiningRegion {
    HONGDAE("홍대/합정"),
    JAMSIL("잠실"),
    GANGNAM("강남/신논현"),
    EULJIRO("을지로/종로");

    private final String label;

    DiningRegion(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 라벨 → 지역. 모르는 라벨(null 포함)이면 INVALID_REGION. */
    public static DiningRegion fromLabel(String label) {
        return Arrays.stream(values())
                .filter(region -> region.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REGION));
    }

    /** 전체 라벨, 선언 순서. GET /api/dining/regions */
    public static List<String> labels() {
        return Arrays.stream(values()).map(DiningRegion::getLabel).toList();
    }

    /** 넘긴 순서(지망 순) 그대로 라벨로 바꾼다. 응답 표시용. */
    public static List<String> labelsOf(Collection<DiningRegion> regions) {
        return regions.stream().map(DiningRegion::getLabel).toList();
    }

    /** DB 컬럼에는 라벨을 저장한다. */
    @Converter
    public static class LabelConverter implements AttributeConverter<DiningRegion, String> {
        @Override
        public String convertToDatabaseColumn(DiningRegion region) {
            return region != null ? region.label : null;
        }

        @Override
        public DiningRegion convertToEntityAttribute(String label) {
            return label != null ? fromLabel(label) : null;
        }
    }
}
