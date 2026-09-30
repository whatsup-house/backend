package com.whatsuphouse.backend.domain.dining.entity;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 매칭 규칙 기본값(단일 행, id = 1). V7이 기본 행을 넣는다. 회차의 동명 컬럼이 NULL이 아니면 회차 값이 우선한다. (설계 2.7)
 * defaults()의 값은 V7의 컬럼 DEFAULT와 같다.
 */
@Entity
@Table(name = "matching_rule_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRuleSetting {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "max_age_gap", nullable = false)
    private int maxAgeGap;

    @Column(name = "table_size_min", nullable = false)
    private int tableSizeMin;

    @Column(name = "table_size_max", nullable = false)
    private int tableSizeMax;

    @Column(name = "min_group_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal minGroupScore;

    @Column(name = "auto_confirm_grace_minutes", nullable = false)
    private int autoConfirmGraceMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private MatchingWeights weights;

    private MatchingRuleSetting(int maxAgeGap, int tableSizeMin, int tableSizeMax, BigDecimal minGroupScore,
                                int autoConfirmGraceMinutes, MatchingWeights weights) {
        this.id = SINGLETON_ID;
        update(maxAgeGap, tableSizeMin, tableSizeMax, minGroupScore, autoConfirmGraceMinutes, weights);
    }

    public static MatchingRuleSetting defaults() {
        return new MatchingRuleSetting(8, 4, 6, new BigDecimal("0.35"), 120, MatchingWeights.defaults());
    }

    public void update(int maxAgeGap, int tableSizeMin, int tableSizeMax, BigDecimal minGroupScore,
                       int autoConfirmGraceMinutes, MatchingWeights weights) {
        if (tableSizeMin > tableSizeMax) {
            throw new CustomException(ErrorCode.INVALID_TABLE_SIZE_RANGE);
        }
        this.maxAgeGap = maxAgeGap;
        this.tableSizeMin = tableSizeMin;
        this.tableSizeMax = tableSizeMax;
        this.minGroupScore = minGroupScore;
        this.autoConfirmGraceMinutes = autoConfirmGraceMinutes;
        this.weights = weights;
    }
}
