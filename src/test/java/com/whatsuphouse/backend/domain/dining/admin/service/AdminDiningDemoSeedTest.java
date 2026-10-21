package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.auth.service.DemoAccountService;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminDiningDemoService.SeedProfile;
import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.matching.service.MatchingEngine;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.whatsuphouse.backend.domain.dining.admin.service.AdminDiningDemoService.SEED_PROFILES;
import static org.assertj.core.api.Assertions.assertThat;

/** 심사용 시드 신청자(KAN-442) 설계 검증: 구성 조건과, 매칭 엔진을 직접 돌린 테이블 점수. */
class AdminDiningDemoSeedTest {

    // 기본 매칭 규칙: 4~6인, 출생연도 차 8, 기본 가중치(연령대 5 · 성비 4 · MBTI 3 · 관심사 2 · 성향 1)
    private static final MatchingEngine.Rules RULES =
            new MatchingEngine.Rules(4, 6, 8, MatchingWeights.defaults(), List.of());

    private final MatchingEngine engine = new MatchingEngine();

    @Test
    @DisplayName("시드 16명: 직업은 체험 계정 직업 목록에서 모두 다르고, 남녀 8:8, 지역마다 8명, 4인 묶음마다 남2·여2·출생연도 차 3년 이내")
    void seedProfiles_composition() {
        assertThat(SEED_PROFILES).hasSize(16);
        assertThat(SEED_PROFILES).extracting(SeedProfile::job).doesNotHaveDuplicates()
                .allMatch(DemoAccountService.JOBS::contains);
        assertThat(SEED_PROFILES.stream().collect(Collectors.groupingBy(SeedProfile::gender, Collectors.counting())))
                .containsOnly(Map.entry(Gender.MALE, 8L), Map.entry(Gender.FEMALE, 8L));
        assertThat(SEED_PROFILES.stream().collect(Collectors.groupingBy(SeedProfile::region, Collectors.counting())))
                .containsOnly(Map.entry(DiningRegion.HONGDAE, 8L), Map.entry(DiningRegion.GANGNAM, 8L));
        for (int g = 0; g < 4; g++) {
            List<SeedProfile> group = SEED_PROFILES.subList(g * 4, g * 4 + 4);
            assertThat(group).filteredOn(p -> p.gender() == Gender.MALE).hasSize(2);
            IntSummaryStatistics years = group.stream().mapToInt(SeedProfile::birthYear).summaryStatistics();
            assertThat(years.getMax() - years.getMin()).isLessThanOrEqualTo(3);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DiningRegion.class, names = {"HONGDAE", "GANGNAM"})
    @DisplayName("엔진이 지역 8명을 설계한 4인 묶음 2개로 나누고, 두 테이블 점수가 모두 0.70 이상이다")
    void seedProfiles_engineSplitsIntoDesignedTablesAbove70(DiningRegion region) {
        // given
        List<MatchingEngine.Applicant> pool = IntStream.range(0, SEED_PROFILES.size())
                .filter(i -> SEED_PROFILES.get(i).region() == region)
                .mapToObj(AdminDiningDemoSeedTest::applicant)
                .toList();

        // when
        MatchingEngine.Result result = engine.match(pool, RULES, MatchingEngine.Relations.none());

        // then
        assertThat(result.unassigned()).isEmpty();
        assertThat(result.tables()).hasSize(2).allSatisfy(table ->
                assertThat(table.score().value().doubleValue()).isGreaterThanOrEqualTo(0.70));
        int first = region == DiningRegion.HONGDAE ? 0 : 8;
        assertThat(result.tables().stream()
                .map(table -> table.seats().stream().map(MatchingEngine.Seat::applicationId).collect(Collectors.toSet()))
                .toList())
                .containsExactlyInAnyOrder(ids(first), ids(first + 4));
    }

    // DiningMatchService.toApplicants와 같은 값: 표준 질문 답 + 회원 직업의 직군
    private static MatchingEngine.Applicant applicant(int index) {
        SeedProfile p = SEED_PROFILES.get(index);
        return new MatchingEngine.Applicant(new UUID(0, index), new UUID(1, index), p.birthYear(), p.gender().name(),
                p.mbti().name(), Set.copyOf(p.interests()), Set.copyOf(p.myStyle()), Set.copyOf(p.wantedStyle()),
                p.job().getCategory().name(), Map.of());
    }

    private static Set<UUID> ids(int from) {
        return IntStream.range(from, from + 4).mapToObj(i -> new UUID(0, i)).collect(Collectors.toSet());
    }
}
