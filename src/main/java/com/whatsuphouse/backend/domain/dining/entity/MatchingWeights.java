package com.whatsuphouse.backend.domain.dining.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 페어 점수 항목별 가중치. matching_rule_settings.weights(JSONB)에 그대로 직렬화된다. (설계 4.4)
 * 필드 순서 = PRD 우선순위(연령대 → 성비 → MBTI → 관심사 → 성향, MAT-04).
 * JSON에 ageBand 키가 없는 기존 행(KAN-435 이전 저장)은 필드 초기값 DEFAULT_AGE_BAND를 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MatchingWeights {

    static final double DEFAULT_AGE_BAND = 5.0;

    private double ageBand = DEFAULT_AGE_BAND;
    private double gender;
    private double mbti;
    private double interests;
    private double wantedStyle;
    private double custom;

    /** 코드 기본값. V7 컬럼 DEFAULT(전부 1.0, ageBand 없음)와 다르다 — DB 기본 행은 저장된 값을 그대로 쓴다. */
    public static MatchingWeights defaults() {
        return new MatchingWeights(DEFAULT_AGE_BAND, 4.0, 3.0, 2.0, 1.0, 1.0);
    }
}
