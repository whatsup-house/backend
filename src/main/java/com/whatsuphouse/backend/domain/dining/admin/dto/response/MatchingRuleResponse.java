package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MatchingRuleResponse {

    @Schema(description = "테이블 내 최대 나이 차", example = "8")
    private int maxAgeGap;

    @Schema(description = "테이블 최소 인원", example = "4")
    private int tableSizeMin;

    @Schema(description = "테이블 최대 인원", example = "6")
    private int tableSizeMax;

    @Schema(description = "그룹 최소 점수", example = "0.3500")
    private BigDecimal minGroupScore;

    @Schema(description = "자동 확정 유예(분)", example = "120")
    private int autoConfirmGraceMinutes;

    @Schema(description = "페어 점수 항목별 가중치")
    private Weights weights;

    public static MatchingRuleResponse from(MatchingRuleSetting setting) {
        return MatchingRuleResponse.builder()
                .maxAgeGap(setting.getMaxAgeGap())
                .tableSizeMin(setting.getTableSizeMin())
                .tableSizeMax(setting.getTableSizeMax())
                .minGroupScore(setting.getMinGroupScore())
                .autoConfirmGraceMinutes(setting.getAutoConfirmGraceMinutes())
                .weights(Weights.from(setting.getWeights()))
                .build();
    }

    @Getter
    @Builder
    public static class Weights {
        @Schema(description = "성별 다양성", example = "1.0")
        private double gender;
        @Schema(description = "MBTI 궁합", example = "1.0")
        private double mbti;
        @Schema(description = "관심사 겹침", example = "1.0")
        private double interests;
        @Schema(description = "원하는 스타일 일치", example = "1.0")
        private double wantedStyle;
        @Schema(description = "커스텀 매칭 질문", example = "1.0")
        private double custom;

        static Weights from(MatchingWeights weights) {
            return Weights.builder()
                    .gender(weights.getGender())
                    .mbti(weights.getMbti())
                    .interests(weights.getInterests())
                    .wantedStyle(weights.getWantedStyle())
                    .custom(weights.getCustom())
                    .build();
        }
    }
}
