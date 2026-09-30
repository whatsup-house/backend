package com.whatsuphouse.backend.domain.dining.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 페어 점수 항목별 가중치. matching_rule_settings.weights(JSONB)에 그대로 직렬화된다. (설계 4.4) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MatchingWeights {

    private double gender;
    private double mbti;
    private double interests;
    private double wantedStyle;
    private double custom;

    public static MatchingWeights defaults() {
        return new MatchingWeights(1.0, 1.0, 1.0, 1.0, 1.0);
    }
}
