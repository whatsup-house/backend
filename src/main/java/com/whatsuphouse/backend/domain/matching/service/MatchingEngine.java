package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.dining.entity.MatchingWeights;
import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.entity.ScoreDetail;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * 우연한 식탁 자동매칭 rule-v2 엔진 (설계 4.3~4.5). 계산만 하고 저장은 DiningMatchService가 한다.
 * - 하드 조건(완화 없음): 인원 [min, max], 출생연도 최대-최소 ≤ maxAgeGap, 제외 관계 쌍 없음.
 *   출생연도를 모르는 사람은 나이 차 계산에서 빠진다.
 * - 페어 점수 = Σ(weight × 항목) / Σweight. 답이 없어 계산할 수 없는 항목은 분자·분모에서 뺀다. Σweight = 0이면 0.
 * - 그룹 점수 = 0.7·pairAvg + 0.3·pairMin − 페널티, 0 미만은 0.
 * - 무작위 없음. 모든 선택의 동점은 입력 순서가 앞선 쪽이라 같은 입력(순서 포함)이면 같은 결과다.
 */
@Component
public class MatchingEngine {

    public static final String ALGORITHM_VERSION = "rule-v2";

    private static final double AVG_WEIGHT = 0.7;
    private static final double MIN_WEIGHT = 0.3;
    static final double PENALTY_ALL_SAME_GENDER = 0.30;
    static final double PENALTY_SINGLE_MINORITY_GENDER = 0.10;
    static final double PENALTY_MET_BEFORE_PER_PAIR = 0.15;
    static final double PENALTY_SAME_JOB_CATEGORY = 0.05;
    static final double PENALTY_SAME_MBTI = 0.05;
    private static final int CROWD_SIZE = 3;

    static final List<String> MBTI_TYPES = List.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTJ", "INTP", "ENTP",
            "ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ");

    // 흔히 쓰는 MBTI 궁합표를 3단계로 줄였다. 2 = 잘 맞음(1.0), 1 = 무난(0.5), 0 = 안 맞음(0.0).
    // 행·열 순서는 MBTI_TYPES, 대칭 행렬이다.
    static final String[] MBTI_COMPATIBILITY = {
            "1112121100000000", // INFP
            "1121211100000000", // ENFP
            "1211111200000000", // INFJ
            "2111111120000000", // ENFJ
            "1211111211111111", // INTJ
            "2111112111111111", // ENTJ
            "1111121111111112", // INTP
            "1121211111111111", // ENTP
            "0002111111111212", // ISFP
            "0000111111112121", // ESFP
            "0000111111111212", // ISTP
            "0000111111112121", // ESTP
            "0000111112121111", // ISFJ
            "0000111121211111", // ESFJ
            "0000111112121111", // ISTJ
            "0000112121211111", // ESTJ
    };

    /** 매칭 대상 한 명. 표준 질문(reserved_key) 답과 커스텀 매칭 질문 답(question_key → 값). */
    public record Applicant(UUID applicationId, UUID userId, Integer birthYear, String gender, String mbti,
                            Set<String> interests, Set<String> myStyle, Set<String> wantedStyle,
                            String jobCategory, Map<String, Object> customAnswers) {
    }

    /** reserved_key가 없는 매칭 질문. 페어 점수의 custom 항목은 이 질문들의 weight 가중평균이다. */
    public record CustomField(String questionKey, MatchingStrategy strategy, double weight) {
    }

    public record Rules(int tableSizeMin, int tableSizeMax, int maxAgeGap, double minGroupScore,
                        MatchingWeights weights, List<CustomField> customFields) {
    }

    /**
     * 회원 ID 기준 관계. blocked는 같은 테이블 금지(하드), metBefore는 이전 같은 테이블(페널티),
     * again은 다시 만나고 싶다고 한 쌍(metBefore 페널티 면제). 한 방향만 있어도 양방향으로 본다.
     */
    public record Relations(Map<UUID, Set<UUID>> blocked, Map<UUID, Set<UUID>> metBefore, Map<UUID, Set<UUID>> again) {
        public Relations(Map<UUID, Set<UUID>> blocked, Map<UUID, Set<UUID>> metBefore) {
            this(blocked, metBefore, Map.of());
        }

        public static Relations none() {
            return new Relations(Map.of(), Map.of(), Map.of());
        }
    }

    public record Seat(UUID applicationId, AssignReason reason) {
    }

    public record Score(BigDecimal value, ScoreDetail detail) {
    }

    public record TableResult(List<Seat> seats, Score score) {
    }

    public record Result(List<TableResult> tables, List<MatchRun.Unassigned> unassigned,
                         int splitCount, int mergeCount, int reallocatedCount) {
    }

    public Result match(List<Applicant> applicants, Rules rules, Relations relations) {
        return new Run(applicants, rules, relations).execute();
    }

    /** 이미 정해진 멤버 구성의 그룹 점수(수동 조정 후 재계산용). */
    public Score score(List<Applicant> members, Rules rules, Relations relations) {
        return new Run(members, rules, relations).score(IntStream.range(0, members.size()).boxed().toList());
    }

    static Double mbtiCompatibility(String a, String b) {
        int i = a == null ? -1 : MBTI_TYPES.indexOf(a);
        int j = b == null ? -1 : MBTI_TYPES.indexOf(b);
        if (i < 0 || j < 0) {
            return null;
        }
        return (MBTI_COMPATIBILITY[i].charAt(j) - '0') / 2.0;
    }

    // ── 실행 1회. 사람은 입력 순서의 인덱스로 다룬다. ───────────────────────────────

    private static final class Run {

        private final List<Applicant> people;
        private final Rules rules;
        private final double[][] pair;
        private final boolean[][] blocked;
        private final boolean[][] met;
        private int splitCount;
        private int mergeCount;
        private int reallocatedCount;

        Run(List<Applicant> people, Rules rules, Relations relations) {
            this.people = people;
            this.rules = rules;
            int n = people.size();
            this.pair = new double[n][n];
            this.blocked = new boolean[n][n];
            this.met = new boolean[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Applicant a = people.get(i);
                    Applicant b = people.get(j);
                    pair[i][j] = pair[j][i] = pairScore(a, b);
                    blocked[i][j] = blocked[j][i] = related(relations.blocked(), a, b);
                    met[i][j] = met[j][i] = related(relations.metBefore(), a, b) && !related(relations.again(), a, b);
                }
            }
        }

        Result execute() {
            List<Draft> tables = new ArrayList<>();
            List<Draft> incomplete = new ArrayList<>();

            // 1~2. 하드 조건을 함께 만족하는 덩어리를 그리디로 만들고 tableSizeMax 이하로 자른다. min 미만 조각은 보류.
            List<Integer> pool = new ArrayList<>(IntStream.range(0, people.size()).boxed().toList());
            while (!pool.isEmpty()) {
                List<List<Integer>> pieces = split(growChunk(pool));
                if (pieces.size() > 1) {
                    splitCount++;
                }
                for (List<Integer> piece : pieces) {
                    (piece.size() >= rules.tableSizeMin() ? tables : incomplete).add(new Draft(piece, AssignReason.INITIAL));
                }
            }

            // 3. 보류 조각끼리 병합
            merge(incomplete, tables);

            // 4. 남은 사람 재배치
            Map<Integer, UnassignedReason> unassigned = new TreeMap<>();
            reallocate(incomplete.stream().flatMap(d -> d.members.stream()).sorted().toList(), tables, unassigned, null);

            // 5. 재검증(MAT-07), 6. 최소 점수 미달 → 해체 후 재배치.
            // 재배치는 하드 조건과 최소 점수를 지키는 테이블에만 넣으므로 두 검사는 한 바퀴로 수렴한다.
            reallocate(dissolveIf(tables, t -> !isValid(t.members)), tables, unassigned, null);
            reallocate(dissolveIf(tables, t -> belowMinScore(t.members)), tables, unassigned, UnassignedReason.LOW_SCORE);

            List<TableResult> results = tables.stream()
                    .map(t -> new TableResult(t.members.stream()
                            .map(i -> new Seat(people.get(i).applicationId(), t.reasons.get(i)))
                            .toList(), score(t.members)))
                    .toList();
            List<MatchRun.Unassigned> unassignedResults = unassigned.entrySet().stream()
                    .map(e -> new MatchRun.Unassigned(people.get(e.getKey()).applicationId(), e.getValue()))
                    .toList();
            return new Result(results, unassignedResults, splitCount, mergeCount, reallocatedCount);
        }

        // ── 그룹 형성 ─────────────────────────────────────────────────────────

        // 시드(가장 배치하기 어려운 사람)에서 시작해 하드 조건을 지키는 한 평균 페어 점수가 가장 높은 사람을 더한다.
        private List<Integer> growChunk(List<Integer> pool) {
            List<Integer> chunk = new ArrayList<>();
            int next = hardestToPlace(pool);
            while (next >= 0) {
                chunk.add(next);
                pool.remove(Integer.valueOf(next));
                next = bestCandidate(chunk, pool);
            }
            return chunk;
        }

        // 덩어리를 max 이하 조각으로 나눈다. 모두 min 이상으로 고르게 나눌 수 있으면 그렇게(13명 → 5·4·4),
        // 안 되면 max씩 자르고 나머지(min 미만)를 따로 둔다(7명 → 6 + 1).
        private List<List<Integer>> split(List<Integer> chunk) {
            int n = chunk.size();
            int min = rules.tableSizeMin();
            int max = rules.tableSizeMax();
            int k = (n + max - 1) / max;
            List<Integer> sizes = new ArrayList<>();
            if (k * min <= n) {
                for (int i = 0; i < k; i++) {
                    sizes.add(n / k + (i < n % k ? 1 : 0));
                }
            } else {
                for (int i = 0; i < k - 1; i++) {
                    sizes.add(max);
                }
            }

            List<Integer> rest = new ArrayList<>(chunk);
            List<List<Integer>> pieces = new ArrayList<>();
            for (int size : sizes) {
                List<Integer> piece = new ArrayList<>();
                int next = hardestToPlace(rest);
                while (next >= 0 && piece.size() < size) {
                    piece.add(next);
                    rest.remove(Integer.valueOf(next));
                    next = bestCandidate(piece, rest);
                }
                pieces.add(piece);
            }
            if (!rest.isEmpty()) {
                pieces.add(rest);
            }
            return pieces;
        }

        // 보류 조각 둘을 합쳐 max 이하이고 하드 조건을 통과하면 병합한다. 큰 결과·높은 점수 순. min에 들면 테이블로 올린다.
        private void merge(List<Draft> incomplete, List<Draft> tables) {
            while (true) {
                int bestI = -1;
                int bestJ = -1;
                int bestSize = 0;
                double bestScore = -1;
                for (int i = 0; i < incomplete.size(); i++) {
                    for (int j = i + 1; j < incomplete.size(); j++) {
                        List<Integer> union = concat(incomplete.get(i).members, incomplete.get(j).members);
                        if (union.size() > rules.tableSizeMax() || !isCompatible(union)) {
                            continue;
                        }
                        double s = scoreValue(union);
                        if (union.size() > bestSize || (union.size() == bestSize && s > bestScore)) {
                            bestI = i;
                            bestJ = j;
                            bestSize = union.size();
                            bestScore = s;
                        }
                    }
                }
                if (bestI < 0) {
                    return;
                }
                Draft merged = new Draft(concat(incomplete.get(bestI).members, incomplete.get(bestJ).members),
                        AssignReason.MERGED);
                incomplete.remove(bestJ);
                incomplete.remove(bestI);
                if (merged.members.size() >= rules.tableSizeMin()) {
                    tables.add(merged);
                    mergeCount++;
                } else {
                    incomplete.add(merged);
                }
            }
        }

        // 자리(< max)가 있고 하드 조건을 통과하며 넣은 뒤 점수가 최소 점수 이상인 테이블 중 점수가 가장 높은 곳에 넣는다.
        private void reallocate(List<Integer> persons, List<Draft> tables, Map<Integer, UnassignedReason> unassigned,
                                UnassignedReason fixedReason) {
            for (int p : persons) {
                Draft best = null;
                double bestScore = -1;
                for (Draft t : tables) {
                    if (t.members.size() >= rules.tableSizeMax() || !fits(t.members, p)) {
                        continue;
                    }
                    double s = scoreValue(concat(t.members, List.of(p)));
                    if (s >= rules.minGroupScore() && s > bestScore) {
                        best = t;
                        bestScore = s;
                    }
                }
                if (best != null) {
                    best.add(p, AssignReason.REALLOCATED);
                    reallocatedCount++;
                } else {
                    unassigned.put(p, fixedReason != null ? fixedReason : reasonFor(p, tables));
                }
            }
        }

        // 넣을 수 있는 테이블이 없었던 이유. 조건은 맞았는데 점수가 모자랐다 > 자리가 없었다 > 제외 관계 > 나이 차.
        private UnassignedReason reasonFor(int p, List<Draft> tables) {
            boolean roomButLowScore = false;
            boolean fitsButFull = false;
            boolean blockedOnly = false;
            for (Draft t : tables) {
                boolean ageOk = ageGap(concat(t.members, List.of(p))) <= rules.maxAgeGap();
                boolean pairOk = t.members.stream().noneMatch(m -> blocked[m][p]);
                if (ageOk && pairOk) {
                    if (t.members.size() < rules.tableSizeMax()) {
                        roomButLowScore = true;
                    } else {
                        fitsButFull = true;
                    }
                } else if (ageOk) {
                    blockedOnly = true;
                }
            }
            if (roomButLowScore) {
                return UnassignedReason.LOW_SCORE;
            }
            if (fitsButFull || tables.isEmpty()) {
                return UnassignedReason.NOT_ENOUGH_PEOPLE;
            }
            return blockedOnly ? UnassignedReason.BLOCKED_PAIR : UnassignedReason.AGE_GAP;
        }

        private List<Integer> dissolveIf(List<Draft> tables, Predicate<Draft> condition) {
            List<Integer> released = new ArrayList<>();
            tables.removeIf(t -> {
                if (!condition.test(t)) {
                    return false;
                }
                released.addAll(t.members);
                return true;
            });
            released.sort(null);
            return released;
        }

        // 풀에서 하드 조건이 맞는 상대가 가장 적은 사람. 풀이 비었으면 -1.
        private int hardestToPlace(List<Integer> pool) {
            int best = -1;
            int bestCount = Integer.MAX_VALUE;
            for (int i : pool) {
                int count = 0;
                for (int j : pool) {
                    if (i != j && fits(List.of(i), j)) {
                        count++;
                    }
                }
                if (count < bestCount) {
                    best = i;
                    bestCount = count;
                }
            }
            return best;
        }

        // group에 넣어도 하드 조건이 맞는 후보 중 group과의 평균 페어 점수가 가장 높은 사람. 없으면 -1.
        private int bestCandidate(List<Integer> group, List<Integer> pool) {
            int best = -1;
            double bestAvg = -1;
            for (int c : pool) {
                if (!fits(group, c)) {
                    continue;
                }
                double avg = group.stream().mapToDouble(m -> pair[m][c]).average().orElse(0);
                if (avg > bestAvg) {
                    best = c;
                    bestAvg = avg;
                }
            }
            return best;
        }

        // ── 하드 조건 ─────────────────────────────────────────────────────────

        private boolean fits(List<Integer> group, int candidate) {
            return group.stream().noneMatch(m -> blocked[m][candidate])
                    && ageGap(concat(group, List.of(candidate))) <= rules.maxAgeGap();
        }

        private boolean isCompatible(List<Integer> group) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    if (blocked[group.get(i)][group.get(j)]) {
                        return false;
                    }
                }
            }
            return ageGap(group) <= rules.maxAgeGap();
        }

        private boolean isValid(List<Integer> group) {
            return group.size() >= rules.tableSizeMin() && group.size() <= rules.tableSizeMax() && isCompatible(group);
        }

        private int ageGap(List<Integer> group) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i : group) {
                Integer year = people.get(i).birthYear();
                if (year != null) {
                    min = Math.min(min, year);
                    max = Math.max(max, year);
                }
            }
            return max >= min ? max - min : 0;
        }

        // ── 점수 ─────────────────────────────────────────────────────────────

        private boolean belowMinScore(List<Integer> group) {
            return scoreValue(group) < rules.minGroupScore();
        }

        private double scoreValue(List<Integer> group) {
            return score(group).value().doubleValue();
        }

        Score score(List<Integer> group) {
            double sum = 0;
            double pairMin = Double.MAX_VALUE;
            int pairs = 0;
            int metPairs = 0;
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    int a = group.get(i);
                    int b = group.get(j);
                    sum += pair[a][b];
                    pairMin = Math.min(pairMin, pair[a][b]);
                    pairs++;
                    if (met[a][b]) {
                        metPairs++;
                    }
                }
            }
            double pairAvg = pairs == 0 ? 0 : sum / pairs;
            pairMin = pairs == 0 ? 0 : pairMin;

            Map<String, Long> genders = countBy(group, Applicant::gender);
            boolean gendersKnown = genders.values().stream().mapToLong(Long::longValue).sum() == group.size();
            Map<String, Double> penalties = new LinkedHashMap<>();
            penalties.put("allSameGender", gendersKnown && group.size() > 1 && genders.size() == 1
                    ? PENALTY_ALL_SAME_GENDER : 0);
            penalties.put("singleMinorityGender", gendersKnown && genders.size() > 1 && genders.containsValue(1L)
                    ? PENALTY_SINGLE_MINORITY_GENDER : 0);
            penalties.put("metBefore", round(metPairs * PENALTY_MET_BEFORE_PER_PAIR));
            penalties.put("sameJobCategory", maxCount(group, Applicant::jobCategory) >= CROWD_SIZE
                    ? PENALTY_SAME_JOB_CATEGORY : 0);
            penalties.put("sameMbti", maxCount(group, Applicant::mbti) >= CROWD_SIZE ? PENALTY_SAME_MBTI : 0);

            double penalty = penalties.values().stream().mapToDouble(Double::doubleValue).sum();
            double value = Math.max(0, AVG_WEIGHT * pairAvg + MIN_WEIGHT * pairMin - penalty);
            return new Score(BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP),
                    new ScoreDetail(round(pairAvg), round(pairMin), penalties));
        }

        private Map<String, Long> countBy(List<Integer> group, Function<Applicant, String> key) {
            Map<String, Long> counts = new HashMap<>();
            for (int i : group) {
                String value = key.apply(people.get(i));
                if (value != null) {
                    counts.merge(value, 1L, Long::sum);
                }
            }
            return counts;
        }

        private long maxCount(List<Integer> group, Function<Applicant, String> key) {
            return countBy(group, key).values().stream().mapToLong(Long::longValue).max().orElse(0);
        }

        private double pairScore(Applicant a, Applicant b) {
            MatchingWeights w = rules.weights();
            double[] weights = {w.getGender(), w.getMbti(), w.getInterests(), w.getWantedStyle(), w.getCustom()};
            Double[] items = {
                    a.gender() != null && b.gender() != null ? (a.gender().equals(b.gender()) ? 0.0 : 1.0) : null,
                    mbtiCompatibility(a.mbti(), b.mbti()),
                    jaccard(a.interests(), b.interests()),
                    average(wantedMatch(a, b), wantedMatch(b, a)),
                    customScore(a, b)
            };
            double sum = 0;
            double weight = 0;
            for (int k = 0; k < items.length; k++) {
                if (items[k] != null) {
                    sum += weights[k] * items[k];
                    weight += weights[k];
                }
            }
            return weight > 0 ? sum / weight : 0;
        }

        // 내 WANTED_STYLE 중 상대 MY_STYLE에 있는 비율. 원하는 스타일을 고르지 않았으면 계산하지 않는다.
        private static Double wantedMatch(Applicant me, Applicant other) {
            if (me.wantedStyle().isEmpty()) {
                return null;
            }
            long hit = me.wantedStyle().stream().filter(other.myStyle()::contains).count();
            return (double) hit / me.wantedStyle().size();
        }

        private Double customScore(Applicant a, Applicant b) {
            double sum = 0;
            double weight = 0;
            for (CustomField field : rules.customFields()) {
                Object va = a.customAnswers().get(field.questionKey());
                Object vb = b.customAnswers().get(field.questionKey());
                Double s = switch (field.strategy()) {
                    case SAME -> va == null || vb == null ? null : (text(va).equals(text(vb)) ? 1.0 : 0.0);
                    case DIVERSE -> va == null || vb == null ? null : (text(va).equals(text(vb)) ? 0.0 : 1.0);
                    case OVERLAP -> jaccard(values(va), values(vb));
                };
                if (s != null) {
                    sum += field.weight() * s;
                    weight += field.weight();
                }
            }
            return weight > 0 ? sum / weight : null;
        }

        private static boolean related(Map<UUID, Set<UUID>> relation, Applicant a, Applicant b) {
            if (a.userId() == null || b.userId() == null) {
                return false;
            }
            return relation.getOrDefault(a.userId(), Set.of()).contains(b.userId())
                    || relation.getOrDefault(b.userId(), Set.of()).contains(a.userId());
        }
    }

    private static final class Draft {
        private final List<Integer> members = new ArrayList<>();
        private final Map<Integer, AssignReason> reasons = new HashMap<>();

        Draft(List<Integer> members, AssignReason reason) {
            members.forEach(i -> add(i, reason));
        }

        void add(int person, AssignReason reason) {
            members.add(person);
            reasons.put(person, reason);
        }
    }

    // ── 값 헬퍼 ──────────────────────────────────────────────────────────────

    private static Double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        long inter = a.stream().filter(b::contains).count();
        return (double) inter / union.size();
    }

    private static Double average(Double a, Double b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : (a + b) / 2;
    }

    private static List<Integer> concat(List<Integer> a, List<Integer> b) {
        List<Integer> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    private static String text(Object value) {
        return Objects.toString(value);
    }

    private static Set<String> values(Object value) {
        Set<String> result = new HashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(Objects::nonNull).map(Object::toString).forEach(result::add);
        } else if (value != null && !value.toString().isBlank()) {
            result.add(value.toString());
        }
        return result;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
