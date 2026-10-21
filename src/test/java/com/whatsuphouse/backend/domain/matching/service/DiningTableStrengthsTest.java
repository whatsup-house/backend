package com.whatsuphouse.backend.domain.matching.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 장점 문구 규칙(설계 6.2): 0.5 이상 항목을 높은 순으로 최대 2개, 없으면 이전 만남 여부로 기본 문구. */
class DiningTableStrengthsTest {

    private static MatchingEngine.Applicant member(int no, String... interests) {
        return new MatchingEngine.Applicant(new UUID(0, no), new UUID(1, no), 1995, null, null,
                Set.of(interests), Set.of(), Set.of(), null, Map.of());
    }

    private static List<String> describe(Map<String, Double> criteriaAvg, List<MatchingEngine.Applicant> members,
                                         MatchingEngine.Relations relations) {
        return DiningTableStrengths.describe(new MatchingEngine.Explanation(List.of(), null, criteriaAvg), members, relations);
    }

    private static Map<String, Double> avg(Object... keyValues) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], (Double) keyValues[i + 1]);
        }
        return map;
    }

    private static final List<MatchingEngine.Applicant> NO_COMMON = List.of(member(0, "전시"), member(1, "와인"));

    @Test
    @DisplayName("0.5 이상 항목만 높은 순으로 2개까지, %는 반올림")
    void describe_topTwoAboveThreshold() {
        // given
        Map<String, Double> criteriaAvg = avg("gender", 0.5, "mbti", 0.6049, "interests", 0.4, "wantedStyle", 0.8);

        // when
        List<String> strengths = describe(criteriaAvg, NO_COMMON, MatchingEngine.Relations.none());

        // then
        assertThat(strengths).containsExactly("대화 스타일 80% 일치하는 테이블이에요!", "MBTI 궁합 60% 테이블이에요!");
    }

    @Test
    @DisplayName("동점이면 대화 스타일 → 관심사 → MBTI → 성비 → 취향 순, 성비는 % 없이")
    void describe_tieAndGenderAndCustom() {
        // when & then
        assertThat(describe(avg("gender", 0.7, "custom", 0.7), NO_COMMON, MatchingEngine.Relations.none()))
                .containsExactly("성비가 고르게 맞춰진 테이블이에요!", "취향이 70% 맞는 테이블이에요!");
        assertThat(describe(avg("custom", 0.549), NO_COMMON, MatchingEngine.Relations.none()))
                .containsExactly("취향이 55% 맞는 테이블이에요!");
    }

    @Test
    @DisplayName("관심사: 멤버 전원이 고른 관심사가 있으면 그 이름(을/를은 받침으로), 없으면 겹침 %")
    void describe_interests() {
        // given
        List<MatchingEngine.Applicant> allLikeWine = List.of(member(0, "와인", "전시"), member(1, "전시", "와인"), member(2, "와인"));
        List<MatchingEngine.Applicant> allLikeExhibition = List.of(member(0, "전시"), member(1, "전시", "와인"));
        List<MatchingEngine.Applicant> oneMissing = List.of(member(0, "와인"), member(1, "와인"), member(2));

        // when & then
        assertThat(describe(avg("interests", 0.6667), allLikeWine, MatchingEngine.Relations.none()))
                .containsExactly("'와인'을 함께 좋아하는 테이블이에요!");
        assertThat(describe(avg("interests", 0.6667), allLikeExhibition, MatchingEngine.Relations.none()))
                .containsExactly("'전시'를 함께 좋아하는 테이블이에요!");
        assertThat(describe(avg("interests", 0.6667), oneMissing, MatchingEngine.Relations.none()))
                .containsExactly("관심사가 67% 겹치는 테이블이에요!");
    }

    @Test
    @DisplayName("0.5 이상이 없으면 이전 만남이 없을 때 '모두 처음…', 같이 앉은 적 있는 쌍이 있으면 '새로운 사람들…'")
    void describe_fallback() {
        // given
        List<MatchingEngine.Applicant> members = List.of(member(0), member(1), member(2));
        MatchingEngine.Relations metBefore = new MatchingEngine.Relations(Map.of(),
                Map.of(members.get(2).userId(), Set.of(members.get(0).userId())));

        // when & then
        assertThat(describe(avg("gender", 0.49), members, MatchingEngine.Relations.none()))
                .containsExactly(DiningTableStrengths.ALL_FIRST_MEET);
        assertThat(describe(Map.of(), members, metBefore)).containsExactly(DiningTableStrengths.NEW_PEOPLE);
    }
}
