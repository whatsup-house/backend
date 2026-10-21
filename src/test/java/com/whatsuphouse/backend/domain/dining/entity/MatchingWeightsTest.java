package com.whatsuphouse.backend.domain.dining.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingWeightsTest {

    @Test
    @DisplayName("ageBand 키가 없는 기존 weights JSON(V7 기본 행)은 나이대 가중치 기본값 5를 쓰고 나머지는 저장값 그대로")
    void deserialize_withoutAgeBand_usesDefault() throws Exception {
        // given
        String legacy = "{\"gender\": 1.0, \"mbti\": 1.0, \"interests\": 1.0, \"wantedStyle\": 1.0, \"custom\": 1.0}";

        // when
        MatchingWeights weights = new ObjectMapper().readValue(legacy, MatchingWeights.class);

        // then
        assertThat(weights.getAgeBand()).isEqualTo(5.0);
        assertThat(weights.getGender()).isEqualTo(1.0);
        assertThat(weights.getCustom()).isEqualTo(1.0);
    }
}
