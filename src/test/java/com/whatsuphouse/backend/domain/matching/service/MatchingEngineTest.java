package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();
    private final LocalDate fallback = LocalDate.of(2026, 8, 1);

    private MatchingEngine.Applicant applicant(int age, String gender, List<String> interests, List<String> dates) {
        return new MatchingEngine.Applicant(UUID.randomUUID(), Map.of(
                "age", age,
                "gender", gender,
                "interests", interests,
                "available_dates", dates,
                "budget", List.of("2만원")
        ));
    }

    private final List<MatchingEngine.MatchingField> fields = List.of(
            new MatchingEngine.MatchingField("age", MatchingStrategy.SAME, 1.0),
            new MatchingEngine.MatchingField("gender", MatchingStrategy.DIVERSE, 1.0),
            new MatchingEngine.MatchingField("interests", MatchingStrategy.OVERLAP, 1.0)
    );

    @Test
    @DisplayName("4명이 모든 절대조건 통과하면 한 그룹으로 묶인다")
    void match_fourCompatible_formsOneGroup() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화", "음악"), List.of("2026-08-01")),
                applicant(27, "FEMALE", List.of("영화", "여행"), List.of("2026-08-01")),
                applicant(30, "MALE", List.of("음악", "운동"), List.of("2026-08-01")),
                applicant(26, "FEMALE", List.of("여행", "독서"), List.of("2026-08-01"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, fallback, 4);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(4);
        assertThat(groups.get(0).eventDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(groups.get(0).score()).isNotNull();
    }

    @Test
    @DisplayName("나이 차이가 ±8을 넘으면 같은 그룹에 못 들어가 그룹이 안 만들어진다")
    void match_ageGapExceeds_noGroup() {
        // 4명 중 한 명만 나이 격차가 큼 → 4명 그룹 구성 불가
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(27, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(30, "MALE", List.of("음악"), List.of("2026-08-01")),
                applicant(60, "FEMALE", List.of("여행"), List.of("2026-08-01")) // 격차 30+
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, fallback, 4);

        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("날짜가 하나도 안 겹치면 같은 그룹 불가")
    void match_noCommonDate_noGroup() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(27, "FEMALE", List.of("영화"), List.of("2026-08-02")),
                applicant(30, "MALE", List.of("음악"), List.of("2026-08-03")),
                applicant(26, "FEMALE", List.of("여행"), List.of("2026-08-04"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, fallback, 4);

        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("2남2여가 4남보다 group_score가 높다 (성별 다양성)")
    void groupScore_balancedGenderHigher() {
        List<MatchingEngine.Applicant> balanced = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "FEMALE", List.of("영화"), List.of("2026-08-01"))
        );
        List<MatchingEngine.Applicant> allMale = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01"))
        );

        var balancedGroup = engine.match(balanced, fields, fallback, 4);
        var maleGroup = engine.match(allMale, fields, fallback, 4);

        assertThat(balancedGroup).hasSize(1);
        assertThat(maleGroup).hasSize(1);
        assertThat(balancedGroup.get(0).score())
                .isGreaterThan(maleGroup.get(0).score());
    }

    @Test
    @DisplayName("현재 그룹 구성으로 group_score를 다시 계산할 수 있다")
    void scoreGroup_recalculatesCurrentGroupScore() {
        List<MatchingEngine.Applicant> group = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(28, "FEMALE", List.of("영화"), List.of("2026-08-01"))
        );

        assertThat(engine.scoreGroup(group, fields)).isEqualByComparingTo("0.8222");
    }

    @Test
    @DisplayName("5명이면 4명 한 그룹 + 1명 미배정")
    void match_fivePeople_oneGroupOneLeftover() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(27, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(30, "MALE", List.of("음악"), List.of("2026-08-01")),
                applicant(26, "FEMALE", List.of("여행"), List.of("2026-08-01")),
                applicant(29, "MALE", List.of("독서"), List.of("2026-08-01"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, fallback, 4);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(4);
    }

    @Test
    @DisplayName("groupSize=3이면 3명씩 그룹을 만든다 (KAN-224)")
    void match_groupSizeThree_formsThreePersonGroups() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(27, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(30, "MALE", List.of("음악"), List.of("2026-08-01"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, fallback, 3);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(3);
    }

    @Test
    @DisplayName("groupSize=6, 7명이면 6인 그룹 1개 + 1명 미배정 (KAN-224)")
    void match_groupSizeSix_sevenPeople_oneGroupOneLeftover() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화"), List.of("2026-08-01")),
                applicant(27, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(30, "MALE", List.of("음악"), List.of("2026-08-01")),
                applicant(26, "FEMALE", List.of("여행"), List.of("2026-08-01")),
                applicant(29, "MALE", List.of("독서"), List.of("2026-08-01")),
                applicant(25, "FEMALE", List.of("영화"), List.of("2026-08-01")),
                applicant(31, "MALE", List.of("음악"), List.of("2026-08-01"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, fallback, 6);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(6);
    }
}
