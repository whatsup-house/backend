package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 입력 순서와 고정 ID만으로 결과가 정해진다(무작위 없음). */
class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    private static MatchingEngine.Rules rules(double minGroupScore) {
        return new MatchingEngine.Rules(4, 6, 8, minGroupScore, MatchingWeights.defaults(), List.of());
    }

    private static MatchingEngine.Applicant person(int no, int birthYear, String gender, String... interests) {
        return new MatchingEngine.Applicant(new UUID(0, no), new UUID(1, no), birthYear, gender, null,
                Set.of(interests), Set.of(), Set.of(), null, Map.of());
    }

    private static List<MatchingEngine.Applicant> sameAge(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> person(i, 1995, i % 2 == 0 ? "MALE" : "FEMALE", "영화"))
                .toList();
    }

    private static Map<UUID, Set<UUID>> block(MatchingEngine.Applicant from, List<MatchingEngine.Applicant> to) {
        return Map.of(from.userId(), to.stream().map(MatchingEngine.Applicant::userId).collect(Collectors.toSet()));
    }

    private static Set<UUID> ids(MatchingEngine.TableResult table) {
        return table.seats().stream().map(MatchingEngine.Seat::applicationId).collect(Collectors.toSet());
    }

    private static Map<UUID, UnassignedReason> unassigned(MatchingEngine.Result result) {
        return result.unassigned().stream()
                .collect(Collectors.toMap(MatchRun.Unassigned::applicationId, MatchRun.Unassigned::reason));
    }

    // 모든 테이블이 인원·나이 차 하드 조건을 지키는지
    private static void assertHardConditions(MatchingEngine.Result result, List<MatchingEngine.Applicant> people) {
        Map<UUID, Integer> years = people.stream()
                .collect(Collectors.toMap(MatchingEngine.Applicant::applicationId, MatchingEngine.Applicant::birthYear));
        for (MatchingEngine.TableResult table : result.tables()) {
            assertThat(table.seats()).hasSizeBetween(4, 6);
            List<Integer> tableYears = ids(table).stream().map(years::get).toList();
            assertThat(tableYears.stream().mapToInt(Integer::intValue).max().orElseThrow()
                    - tableYears.stream().mapToInt(Integer::intValue).min().orElseThrow()).isLessThanOrEqualTo(8);
        }
    }

    @Test
    @DisplayName("출생연도 차가 maxAgeGap을 넘는 사람은 같은 테이블에 앉지 않고 AGE_GAP으로 남는다")
    void match_ageGap_isHardCondition() {
        List<MatchingEngine.Applicant> people = List.of(
                person(0, 1995, "MALE", "영화"), person(1, 1996, "FEMALE", "영화"),
                person(2, 1997, "MALE", "영화"), person(3, 1995, "FEMALE", "영화"),
                person(4, 1980, "MALE", "영화"), person(5, 1980, "FEMALE", "영화"));

        MatchingEngine.Result result = engine.match(people, rules(0), MatchingEngine.Relations.none());

        assertThat(result.tables()).singleElement()
                .satisfies(table -> assertThat(ids(table)).containsExactlyInAnyOrder(
                        new UUID(0, 0), new UUID(0, 1), new UUID(0, 2), new UUID(0, 3)));
        assertThat(unassigned(result)).containsExactlyInAnyOrderEntriesOf(Map.of(
                new UUID(0, 4), UnassignedReason.AGE_GAP, new UUID(0, 5), UnassignedReason.AGE_GAP));
        assertHardConditions(result, people);
    }

    @ParameterizedTest(name = "{0}명 → 테이블 {1}개, 미배정 {2}명")
    @CsvSource({"7, 1, 1", "11, 2, 0", "13, 3, 0"})
    @DisplayName("7명 이상 덩어리는 max 이하로 나눈다. 고르게 나눌 수 있으면 모두 앉힌다(13 → 5·4·4)")
    void match_splitsLargePool(int count, int tableCount, int unassignedCount) {
        List<MatchingEngine.Applicant> people = sameAge(count);

        MatchingEngine.Result result = engine.match(people, rules(0), MatchingEngine.Relations.none());

        assertThat(result.tables()).hasSize(tableCount);
        assertThat(result.unassigned()).hasSize(unassignedCount)
                .allSatisfy(u -> assertThat(u.reason()).isEqualTo(UnassignedReason.NOT_ENOUGH_PEOPLE));
        assertThat(result.splitCount()).isEqualTo(1);
        assertThat(result.tables().stream().mapToInt(t -> t.seats().size()).sum() + unassignedCount).isEqualTo(count);
        assertHardConditions(result, people);
        // 결정적: 같은 입력이면 같은 결과
        assertThat(engine.match(people, rules(0), MatchingEngine.Relations.none())).isEqualTo(result);
    }

    @Test
    @DisplayName("자르고 남은 조각은 다른 미완성 조각과 합쳐 MERGED 테이블이 된다")
    void match_mergesIncompletePieces() {
        // a0~a6: 한 덩어리(7명) → 6명 + 나머지 1명(관심사가 달라 점수가 가장 낮은 a6). b0~b2는 a0과 제외 관계라 따로 3명.
        List<MatchingEngine.Applicant> a = new ArrayList<>(IntStream.range(0, 6)
                .mapToObj(i -> person(i, 1995, i % 2 == 0 ? "MALE" : "FEMALE", "영화")).toList());
        a.add(person(6, 1995, "FEMALE", "등산"));
        List<MatchingEngine.Applicant> b = List.of(
                person(10, 1995, "MALE", "영화"), person(11, 1995, "FEMALE", "영화"), person(12, 1995, "MALE", "영화"));
        List<MatchingEngine.Applicant> people = new ArrayList<>(a);
        people.addAll(b);

        MatchingEngine.Result result = engine.match(people, rules(0),
                new MatchingEngine.Relations(block(a.get(0), b), Map.of()));

        assertThat(result.tables()).hasSize(2);
        assertThat(result.unassigned()).isEmpty();
        assertThat(result.splitCount()).isEqualTo(1);
        assertThat(result.mergeCount()).isEqualTo(1);
        MatchingEngine.TableResult merged = result.tables().get(1);
        assertThat(ids(merged)).containsExactlyInAnyOrder(new UUID(0, 6), new UUID(0, 10), new UUID(0, 11), new UUID(0, 12));
        assertThat(merged.seats()).allSatisfy(seat -> assertThat(seat.reason()).isEqualTo(AssignReason.MERGED));
        assertThat(ids(result.tables().get(0))).doesNotContain(new UUID(0, 10), new UUID(0, 11), new UUID(0, 12));
        assertHardConditions(result, people);
    }

    @Test
    @DisplayName("남은 사람은 자리가 있고 하드 조건이 맞는 테이블에 REALLOCATED로 들어간다")
    void match_reallocatesLeftoverIntoTableWithRoom() {
        // a0~a6 → 6명 + a6. b0~b3(4명, a0과 제외 관계)은 자기들끼리 테이블. 남은 a6은 b 테이블(4명 < 6)에 들어간다.
        List<MatchingEngine.Applicant> a = new ArrayList<>(IntStream.range(0, 6)
                .mapToObj(i -> person(i, 1995, i % 2 == 0 ? "MALE" : "FEMALE", "영화")).toList());
        a.add(person(6, 1995, "FEMALE", "등산"));
        List<MatchingEngine.Applicant> b = List.of(person(10, 1995, "MALE", "영화"), person(11, 1995, "FEMALE", "영화"),
                person(12, 1995, "MALE", "영화"), person(13, 1995, "FEMALE", "영화"));
        List<MatchingEngine.Applicant> people = new ArrayList<>(a);
        people.addAll(b);

        MatchingEngine.Result result = engine.match(people, rules(0),
                new MatchingEngine.Relations(block(a.get(0), b), Map.of()));

        assertThat(result.unassigned()).isEmpty();
        assertThat(result.reallocatedCount()).isEqualTo(1);
        MatchingEngine.TableResult bTable = result.tables().stream()
                .filter(t -> ids(t).contains(new UUID(0, 10))).findFirst().orElseThrow();
        assertThat(bTable.seats()).hasSize(5)
                .contains(new MatchingEngine.Seat(new UUID(0, 6), AssignReason.REALLOCATED));
        assertHardConditions(result, people);
    }

    @Test
    @DisplayName("최소 점수 미달 테이블은 해체되고, 들어갈 곳이 없으면 LOW_SCORE로 남는다")
    void match_dissolvesLowScoreTable() {
        // 전원 남성(−0.30) + 관심사 겹침 없음 → 0점
        List<MatchingEngine.Applicant> people = List.of(person(0, 1995, "MALE", "영화"), person(1, 1995, "MALE", "음악"),
                person(2, 1995, "MALE", "독서"), person(3, 1995, "MALE", "여행"));

        MatchingEngine.Result lenient = engine.match(people, rules(0), MatchingEngine.Relations.none());
        MatchingEngine.Result strict = engine.match(people, rules(0.35), MatchingEngine.Relations.none());

        assertThat(lenient.tables()).hasSize(1);
        assertThat(lenient.tables().get(0).score().value()).isEqualByComparingTo("0");
        assertThat(strict.tables()).isEmpty();
        assertThat(strict.unassigned()).hasSize(4)
                .allSatisfy(u -> assertThat(u.reason()).isEqualTo(UnassignedReason.LOW_SCORE));
    }

    @Test
    @DisplayName("제외 관계인 두 사람은 같은 테이블에 앉지 않고, 자리를 못 찾으면 BLOCKED_PAIR로 남는다")
    void match_excludedPair_neverSeatedTogether() {
        List<MatchingEngine.Applicant> people = sameAge(5);

        MatchingEngine.Result result = engine.match(people, rules(0),
                new MatchingEngine.Relations(block(people.get(1), List.of(people.get(0))), Map.of()));

        // 시드는 동점(제외 관계로 상대가 3명)인 0번과 1번 중 입력이 앞선 0번
        assertThat(result.tables()).singleElement()
                .satisfies(table -> assertThat(ids(table)).containsExactlyInAnyOrder(
                        new UUID(0, 0), new UUID(0, 2), new UUID(0, 3), new UUID(0, 4)));
        assertThat(unassigned(result)).containsExactlyEntriesOf(Map.of(new UUID(0, 1), UnassignedReason.BLOCKED_PAIR));
    }

    @Test
    @DisplayName("이전에 같은 테이블에 앉았던 쌍은 페어당 0.15 감점된다")
    void score_metBeforePenalty() {
        List<MatchingEngine.Applicant> people = sameAge(4); // 남·여·남·여, 관심사 같음

        MatchingEngine.Score fresh = engine.score(people, rules(0), MatchingEngine.Relations.none());
        MatchingEngine.Score met = engine.score(people, rules(0),
                new MatchingEngine.Relations(Map.of(), block(people.get(0), List.of(people.get(1)))));

        // 페어: 남녀 4쌍 1.0, 동성 2쌍 0.5 → 0.7 × 5/6 + 0.3 × 0.5
        assertThat(fresh.value()).isEqualByComparingTo("0.7333");
        assertThat(met.value()).isEqualByComparingTo("0.5833");
        assertThat(met.detail().penalties()).containsEntry("metBefore", 0.15);
        assertThat(fresh.detail().pairMin()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("이전에 만났어도 한쪽이 AGAIN을 남긴 쌍은 페널티가 면제된다")
    void score_againPair_exemptFromMetBeforePenalty() {
        List<MatchingEngine.Applicant> people = sameAge(4);
        Map<UUID, Set<UUID>> met = Map.of(
                people.get(0).userId(), Set.of(people.get(1).userId(), people.get(2).userId()));

        MatchingEngine.Score penalized = engine.score(people, rules(0), new MatchingEngine.Relations(Map.of(), met));
        // 1번이 0번에게 AGAIN(방향 무관) → 0-1 쌍만 면제, 0-2 쌍은 그대로 감점
        MatchingEngine.Score again = engine.score(people, rules(0), new MatchingEngine.Relations(Map.of(), met,
                block(people.get(1), List.of(people.get(0)))));

        assertThat(penalized.detail().penalties()).containsEntry("metBefore", 0.30);
        assertThat(again.detail().penalties()).containsEntry("metBefore", 0.15);
        assertThat(again.value()).isEqualByComparingTo("0.5833");
    }

    @Test
    @DisplayName("MBTI 궁합표는 대칭이고 값은 0 / 0.5 / 1이다")
    void mbtiCompatibility_isSymmetric() {
        for (String a : MatchingEngine.MBTI_TYPES) {
            for (String b : MatchingEngine.MBTI_TYPES) {
                assertThat(MatchingEngine.mbtiCompatibility(a, b))
                        .isEqualTo(MatchingEngine.mbtiCompatibility(b, a))
                        .isIn(0.0, 0.5, 1.0);
            }
        }
        assertThat(MatchingEngine.mbtiCompatibility("INFP", "ENFJ")).isEqualTo(1.0);
        assertThat(MatchingEngine.mbtiCompatibility("INFP", "ESTJ")).isEqualTo(0.0);
        assertThat(MatchingEngine.mbtiCompatibility("INFP", null)).isNull();
    }
}
