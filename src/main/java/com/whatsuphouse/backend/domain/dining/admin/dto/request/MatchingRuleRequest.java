package com.whatsuphouse.backend.domain.dining.admin.dto.request;

import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MatchingRuleRequest {

    @NotNull
    @Min(0)
    @Max(100)
    @Schema(description = "테이블 내 최대 나이 차(출생연도 기준)", example = "8")
    private Integer maxAgeGap;

    // 기존 매칭 인원 보정 범위(2~8)와 같다. 최소 ≤ 최대는 엔티티에서 검사한다.
    @NotNull
    @Min(2)
    @Max(8)
    @Schema(description = "테이블 최소 인원", example = "4")
    private Integer tableSizeMin;

    @NotNull
    @Min(2)
    @Max(8)
    @Schema(description = "테이블 최대 인원", example = "6")
    private Integer tableSizeMax;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 4)
    @Schema(description = "그룹 최소 점수. 미달 테이블은 해체 후 재배치", example = "0.35")
    private BigDecimal minGroupScore;

    @NotNull
    @Min(0)
    @Max(10080)
    @Schema(description = "자동 확정 유예(분). 0이면 즉시 확정", example = "120")
    private Integer autoConfirmGraceMinutes;

    @NotNull
    @Valid
    @Schema(description = "페어 점수 항목별 가중치")
    private WeightsRequest weights;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightsRequest {

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        @Schema(description = "성별 다양성", example = "1.0")
        private Double gender;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        @Schema(description = "MBTI 궁합", example = "1.0")
        private Double mbti;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        @Schema(description = "관심사 겹침", example = "1.0")
        private Double interests;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        @Schema(description = "원하는 스타일 일치", example = "1.0")
        private Double wantedStyle;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        @Schema(description = "커스텀 매칭 질문", example = "1.0")
        private Double custom;

        public MatchingWeights toWeights() {
            return new MatchingWeights(gender, mbti, interests, wantedStyle, custom);
        }
    }
}
