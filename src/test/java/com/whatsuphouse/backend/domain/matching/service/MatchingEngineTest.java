package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    private MatchingEngine.Applicant applicant(int age, String gender, List<String> interests) {
        return new MatchingEngine.Applicant(UUID.randomUUID(), Map.of(
                "age", age,
                "gender", gender,
                "interests", interests
        ));
    }

    private final List<MatchingEngine.MatchingField> fields = List.of(
            new MatchingEngine.MatchingField("age", MatchingStrategy.SAME, 1.0),
            new MatchingEngine.MatchingField("gender", MatchingStrategy.DIVERSE, 1.0),
            new MatchingEngine.MatchingField("interests", MatchingStrategy.OVERLAP, 1.0)
    );

    @Test
    @DisplayName("4명이면 한 그룹으로 묶인다")
    void match_fourPeople_formsOneGroup() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화", "음악")),
                applicant(27, "FEMALE", List.of("영화", "여행")),
                applicant(30, "MALE", List.of("음악", "운동")),
                applicant(26, "FEMALE", List.of("여행", "독서"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, 4);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(4);
        assertThat(groups.get(0).score()).isNotNull();
    }

    @Test
    @DisplayName("2남2여가 4남보다 group_score가 높다 (성별 다양성)")
    void groupScore_balancedGenderHigher() {
        List<MatchingEngine.Applicant> balanced = List.of(
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "FEMALE", List.of("영화")),
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "FEMALE", List.of("영화"))
        );
        List<MatchingEngine.Applicant> allMale = List.of(
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "MALE", List.of("영화"))
        );

        var balancedGroup = engine.match(balanced, fields, 4);
        var maleGroup = engine.match(allMale, fields, 4);

        assertThat(balancedGroup).hasSize(1);
        assertThat(maleGroup).hasSize(1);
        assertThat(balancedGroup.get(0).score())
                .isGreaterThan(maleGroup.get(0).score());
    }

    @Test
    @DisplayName("현재 그룹 구성으로 group_score를 다시 계산할 수 있다")
    void scoreGroup_recalculatesCurrentGroupScore() {
        List<MatchingEngine.Applicant> group = List.of(
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "FEMALE", List.of("영화")),
                applicant(28, "MALE", List.of("영화")),
                applicant(28, "FEMALE", List.of("영화"))
        );

        assertThat(engine.scoreGroup(group, fields)).isEqualByComparingTo("0.8222");
    }

    @Test
    @DisplayName("5명이면 4명 한 그룹 + 1명 미배정")
    void match_fivePeople_oneGroupOneLeftover() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화")),
                applicant(27, "FEMALE", List.of("영화")),
                applicant(30, "MALE", List.of("음악")),
                applicant(26, "FEMALE", List.of("여행")),
                applicant(29, "MALE", List.of("독서"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, 4);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(4);
    }

    @Test
    @DisplayName("groupSize=3이면 3명씩 그룹을 만든다 (KAN-224)")
    void match_groupSizeThree_formsThreePersonGroups() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화")),
                applicant(27, "FEMALE", List.of("영화")),
                applicant(30, "MALE", List.of("음악"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, 3);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(3);
    }

    @Test
    @DisplayName("groupSize=6, 7명이면 6인 그룹 1개 + 1명 미배정 (KAN-224)")
    void match_groupSizeSix_sevenPeople_oneGroupOneLeftover() {
        List<MatchingEngine.Applicant> applicants = List.of(
                applicant(28, "MALE", List.of("영화")),
                applicant(27, "FEMALE", List.of("영화")),
                applicant(30, "MALE", List.of("음악")),
                applicant(26, "FEMALE", List.of("여행")),
                applicant(29, "MALE", List.of("독서")),
                applicant(25, "FEMALE", List.of("영화")),
                applicant(31, "MALE", List.of("음악"))
        );

        List<MatchingEngine.GroupResult> groups = engine.match(applicants, fields, 6);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).applicationIds()).hasSize(6);
    }
}
