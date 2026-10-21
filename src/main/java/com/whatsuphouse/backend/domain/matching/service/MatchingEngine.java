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
import java.util.Arrays;
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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * 우연한 식탁 자동매칭 rule-v2 엔진 (설계 4.3~4.5). 계산만 하고 저장은 DiningMatchService가 한다.
 * - 하드 조건(완화 없음): 인원 [min, max], 출생연도 최대-최소 ≤ maxAgeGap, 제외 관계 쌍 없음.
 *   출생연도를 모르는 사람은 나이 차 계산에서 빠진다.
 * - 분할(KAN-420): 서로 하드 조건이 맞는 덩어리마다 가능한 테이블 수별로 [min, max] 크기 조합(12 → 6·6, 4·4·4)을 시도한다.
 *   조합마다 그리디로 채운 뒤 한 명 옮기기·두 명 맞바꾸기 국소 탐색으로 다듬고, 목적함수
 *   (최저 테이블 점수 ↑, 평균 테이블 점수 ↑, 테이블 수 ↓ 순)가 가장 좋은 구성을 고른다. 딱 나눠지지 않으면(7명 → 6 + 1)
 *   가장 적은 인원을 남기고, 누구를 남길지도 같은 목적함수로 정한다. 남은 사람은 병합·재배치로 간다.
 * - 페어 점수 = Σ(weight × 항목) / Σweight. 답이 없어 계산할 수 없는 항목은 분자·분모에서 뺀다. Σweight = 0이면 0.
 * - 그룹 점수 = 0.7·pairAvg + 0.3·pairMin − 페널티, 0 미만은 0.
 *   점수는 조합을 고르는 데만 쓰고 테이블을 거절하지 않는다. 점수 미달로 미배정되는 사람은 없다. (KAN-420)
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
    // ponytail: 국소 탐색 바퀴 상한. 회차 60명 안팎을 가정한 값. 수백 명이 되면 시간 상한을 두거나 탐색 이웃을 줄인다.
    private static final int MAX_SEARCH_PASSES = 200;
    private static final double EPS = 1e-9;

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

    public record Rules(int tableSizeMin, int tableSizeMax, int maxAgeGap,
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

    /** 페어 점수 항목 키. 순서는 가중치(MatchingWeights)·pairScore 항목 순서와 같다. */
    static final List<String> CRITERIA = List.of("gender", "mbti", "interests", "wantedStyle", "custom");

    /** 쌍별 점수. a·b = applicationId, components = CRITERIA 항목 값(답이 없어 계산할 수 없는 항목은 키 없음). */
    public record PairScore(UUID a, UUID b, double score, Map<String, Double> components) {
    }

    /** 매칭 근거(KAN-432). criteriaAvg = 항목별 쌍 평균(그 항목 값이 있는 쌍만), 값 있는 항목만 CRITERIA 순. */
    public record Explanation(List<PairScore> pairs, Score group, Map<String, Double> criteriaAvg) {
    }

    public Result match(List<Applicant> applicants, Rules rules, Relations relations) {
        return new Run(applicants, rules, relations).execute();
    }

    /** 이미 정해진 멤버 구성의 그룹 점수(수동 조정 후 재계산용). */
    public Score score(List<Applicant> members, Rules rules, Relations relations) {
        return new Run(members, rules, relations).score(IntStream.range(0, members.size()).boxed().toList());
    }

    /** 이미 정해진 멤버 구성의 쌍별 점수·항목 값과 그룹 점수. 저장하지 않고 조회 때 현재 멤버로 계산한다. (KAN-432) */
    public Explanation explain(List<Applicant> members, Rules rules, Relations relations) {
        return new Run(members, rules, relations).explain();
    }

    /** 하드 조건(설계 4.3) 항목. 수동 조정·재조정 재검증의 위반 사유로 노출된다. */
    public enum HardRule {
        TABLE_SIZE,
        AGE_GAP,
        BLOCKED_PAIR
    }

    /** 이미 정해진 멤버 구성의 하드 조건 위반. 비어 있으면 통과. (수동 조정·재조정 재검증용, KAN-347) */
    public List<HardRule> violations(List<Applicant> members, Rules rules, Relations relations) {
        return new Run(members, rules, relations).violations(IntStream.range(0, members.size()).boxed().toList());
    }

    /** 이미 정해진 멤버 구성이 하드 조건(인원 [min, max], 나이 차, 제외 관계)을 지키는지. 확정 직전 최종 검증용. (KAN-346) */
    public boolean satisfiesHardConditions(List<Applicant> members, Rules rules, Relations relations) {
        return violations(members, rules, relations).isEmpty();
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
        // 성별·직군·MBTI를 사람별 정수 ID로(-1 = 모름). 국소 탐색에서 그룹 점수를 맵 없이 빠르게 계산하려고 둔다.
        private final int[] genderId;
        private final int[] jobId;
        private final int[] mbtiId;
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
            this.genderId = ids(people, Applicant::gender);
            this.jobId = ids(people, Applicant::jobCategory);
            this.mbtiId = ids(people, Applicant::mbti);
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

            // 1~2. 하드 조건을 함께 만족하는 덩어리를 그리디로 만들고 [min, max] 테이블로 나눈다. 남는 사람은 보류.
            List<Integer> pool = new ArrayList<>(IntStream.range(0, people.size()).boxed().toList());
            while (!pool.isEmpty()) {
                Partition part = partition(growChunk(pool));
                if (part.tables().size() + (part.bench().isEmpty() ? 0 : 1) > 1) {
                    splitCount++;
                }
                part.tables().forEach(t -> tables.add(new Draft(t, AssignReason.INITIAL)));
                if (!part.bench().isEmpty()) {
                    incomplete.add(new Draft(part.bench(), AssignReason.INITIAL));
                }
            }

            // 3. 보류 조각끼리 병합
            merge(incomplete, tables);

            // 4. 남은 사람 재배치
            Map<Integer, UnassignedReason> unassigned = new TreeMap<>();
            reallocate(incomplete.stream().flatMap(d -> d.members.stream()).sorted().toList(), tables, unassigned);

            // 5. 재검증(MAT-07). 재배치는 하드 조건을 지키는 테이블에만 넣으므로 한 바퀴로 수렴한다.
            // 최소 점수 미달 해체는 하지 않는다(KAN-420).
            reallocate(dissolveIf(tables, t -> !isValid(t.members)), tables, unassigned);

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

        // ── 분할 (KAN-420) ────────────────────────────────────────────────────

        /** 덩어리 분할 결과. tables는 모두 [min, max] 크기, bench는 테이블에 못 넣은 사람(가장 적게). */
        private record Partition(List<List<Integer>> tables, List<Integer> bench, double minScore, double avgScore) {

            // 최저 테이블 점수 ↑ → 평균 테이블 점수 ↑ → 테이블 수 ↓. 합이 아니라 평균이라 테이블 수가 많다고 유리하지 않다(R29).
            boolean betterThan(Partition other) {
                if (Math.abs(minScore - other.minScore) > EPS) {
                    return minScore > other.minScore;
                }
                if (Math.abs(avgScore - other.avgScore) > EPS) {
                    return avgScore > other.avgScore;
                }
                return tables.size() < other.tables.size();
            }
        }

        // 덩어리를 [min, max] 크기 조합마다 나눠 보고 목적함수가 가장 좋은 구성을 고른다. 동점이면 먼저 시도한 조합.
        // 덩어리는 growChunk가 서로 하드 조건이 맞는 사람만 모은 것이라 어떻게 나눠도 나이 차·제외 관계를 지킨다.
        private Partition partition(List<Integer> chunk) {
            Partition best = null;
            for (List<Integer> sizes : sizeCombinations(chunk.size())) {
                Partition candidate = improve(build(chunk, sizes));
                if (best == null || candidate.betterThan(best)) {
                    best = candidate;
                }
            }
            return best != null ? best : new Partition(List.of(), chunk, 0, 0);
        }

        // 남는 인원이 가장 적은 착석 인원에서, 가능한 테이블 수마다 가장 고른 크기 조합(14명 → 5·5·4, 6·4·4가 아니라).
        // 같은 테이블 수의 다른 크기 조합은 국소 탐색의 한 명 옮기기로 닿으므로 출발점은 테이블 수마다 하나면 된다.
        // 7명(4~6) → [6] + 1명. 앉힐 수 없으면 빈 목록.
        private List<List<Integer>> sizeCombinations(int n) {
            int min = Math.max(1, rules.tableSizeMin());
            int max = rules.tableSizeMax();
            for (int seated = n; seated >= min; seated--) {
                List<List<Integer>> found = new ArrayList<>();
                for (int count = (seated + max - 1) / max; count * min <= seated; count++) {
                    int tables = count;
                    int total = seated;
                    found.add(IntStream.range(0, tables).mapToObj(i -> total / tables + (i < total % tables ? 1 : 0)).toList());
                }
                if (!found.isEmpty()) {
                    return found;
                }
            }
            return List.of();
        }

        // 크기마다 시드(가장 배치하기 어려운 사람)에서 시작해 평균 페어 점수가 가장 높은 사람으로 채운다. 남는 사람은 bench.
        private Partition build(List<Integer> chunk, List<Integer> sizes) {
            List<Integer> rest = new ArrayList<>(chunk);
            List<List<Integer>> tables = new ArrayList<>();
            for (int size : sizes) {
                List<Integer> table = new ArrayList<>();
                int next = hardestToPlace(rest);
                while (next >= 0 && table.size() < size) {
                    table.add(next);
                    rest.remove(Integer.valueOf(next));
                    next = bestCandidate(table, rest);
                }
                tables.add(table);
            }
            return evaluate(tables, rest);
        }

        // 결정적 국소 탐색: 사람 순서대로 한 명 옮기기(크기는 [min, max] 유지), 두 명 맞바꾸기(bench와도)를 시도해
        // 목적함수가 엄격히 좋아질 때만 받아들인다. 한 바퀴 동안 개선이 없거나 MAX_SEARCH_PASSES에 닿으면 멈춘다.
        // 옮기기·맞바꾸기는 테이블 수와 bench 인원을 바꾸지 않는다.
        private Partition improve(Partition start) {
            List<List<Integer>> tables = new ArrayList<>();
            start.tables().forEach(t -> tables.add(new ArrayList<>(t)));
            List<Integer> bench = new ArrayList<>(start.bench());
            Search search = new Search(tables, tables.stream().mapToDouble(this::rawScore).toArray(),
                    start.minScore(), start.avgScore());
            // 사람 → 앉은 테이블 번호(bench는 −1)
            int[] where = new int[people.size()];
            List<Integer> everyone = new ArrayList<>(bench);
            bench.forEach(p -> where[p] = -1);
            for (int t = 0; t < tables.size(); t++) {
                for (int p : tables.get(t)) {
                    where[p] = t;
                    everyone.add(p);
                }
            }
            everyone.sort(null);

            for (int pass = 0; pass < MAX_SEARCH_PASSES; pass++) {
                boolean improved = false;
                for (int pi = 0; pi < everyone.size(); pi++) {
                    int p = everyone.get(pi);
                    // 한 명 옮기기
                    int a = where[p];
                    if (a >= 0 && tables.get(a).size() > rules.tableSizeMin()) {
                        for (int b = 0; b < tables.size(); b++) {
                            if (b == a || tables.get(b).size() >= rules.tableSizeMax()) {
                                continue;
                            }
                            List<Integer> newA = new ArrayList<>(tables.get(a));
                            newA.remove(Integer.valueOf(p));
                            List<Integer> newB = new ArrayList<>(tables.get(b));
                            newB.add(p);
                            if (search.tryApply(a, newA, b, newB)) {
                                where[p] = b;
                                improved = true;
                                break;
                            }
                        }
                    }
                    // 두 명 맞바꾸기(뒤 순번 상대만 — 같은 쌍을 두 번 보지 않는다)
                    for (int qi = pi + 1; qi < everyone.size(); qi++) {
                        int q = everyone.get(qi);
                        a = where[p];
                        int b = where[q];
                        if (a == b) {
                            continue;
                        }
                        if (search.tryApply(a, a >= 0 ? replaced(tables.get(a), p, q) : null,
                                b, b >= 0 ? replaced(tables.get(b), q, p) : null)) {
                            if (a < 0) {
                                bench.set(bench.indexOf(p), q);
                            }
                            if (b < 0) {
                                bench.set(bench.indexOf(q), p);
                            }
                            where[p] = b;
                            where[q] = a;
                            improved = true;
                        }
                    }
                }
                if (!improved) {
                    break;
                }
            }
            return evaluate(tables, bench);
        }

        /** 국소 탐색 상태: 테이블 구성과 테이블별 점수, 현재 목적함수 값. */
        private final class Search {
            private final List<List<Integer>> tables;
            private final double[] scores;
            private double min;
            private double avg;

            Search(List<List<Integer>> tables, double[] scores, double min, double avg) {
                this.tables = tables;
                this.scores = scores;
                this.min = min;
                this.avg = avg;
            }

            // 테이블 a·b(−1이면 bench라 점수와 무관)를 newA·newB로 바꿨을 때 목적함수가 엄격히 좋아지면 반영한다.
            boolean tryApply(int a, List<Integer> newA, int b, List<Integer> newB) {
                double scoreA = a >= 0 ? rawScore(newA) : 0;
                double scoreB = b >= 0 ? rawScore(newB) : 0;
                double newMin = Double.MAX_VALUE;
                double newSum = 0;
                for (int t = 0; t < scores.length; t++) {
                    double value = t == a ? scoreA : t == b ? scoreB : scores[t];
                    newMin = Math.min(newMin, value);
                    newSum += value;
                }
                double newAvg = newSum / scores.length;
                boolean better = Math.abs(newMin - min) > EPS ? newMin > min : newAvg > avg + EPS;
                if (!better) {
                    return false;
                }
                if (a >= 0) {
                    tables.set(a, newA);
                    scores[a] = scoreA;
                }
                if (b >= 0) {
                    tables.set(b, newB);
                    scores[b] = scoreB;
                }
                min = newMin;
                avg = newAvg;
                return true;
            }
        }

        private Partition evaluate(List<List<Integer>> tables, List<Integer> bench) {
            double[] scores = tables.stream().mapToDouble(this::rawScore).toArray();
            double sum = 0;
            for (double score : scores) {
                sum += score;
            }
            return new Partition(tables.stream().map(List::copyOf).toList(), List.copyOf(bench),
                    Arrays.stream(scores).min().orElse(0), scores.length == 0 ? 0 : sum / scores.length);
        }

        private static List<Integer> replaced(List<Integer> group, int out, int in) {
            List<Integer> result = new ArrayList<>(group);
            result.set(result.indexOf(out), in);
            return result;
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

        // 자리(< max)가 있고 하드 조건을 통과하는 테이블 중 넣은 뒤 점수가 가장 높은 곳에 넣는다.
        private void reallocate(List<Integer> persons, List<Draft> tables, Map<Integer, UnassignedReason> unassigned) {
            for (int p : persons) {
                Draft best = null;
                double bestScore = -1;
                for (Draft t : tables) {
                    if (t.members.size() >= rules.tableSizeMax() || !fits(t.members, p)) {
                        continue;
                    }
                    double s = scoreValue(concat(t.members, List.of(p)));
                    if (s > bestScore) {
                        best = t;
                        bestScore = s;
                    }
                }
                if (best != null) {
                    best.add(p, AssignReason.REALLOCATED);
                    reallocatedCount++;
                } else {
                    unassigned.put(p, reasonFor(p, tables));
                }
            }
        }

        // 넣을 수 있는 테이블이 없었던 이유. 자리가 없었다 > 제외 관계 > 나이 차.
        // 자리가 있고 조건이 맞는 테이블이 있었다면 reallocate가 이미 넣었으므로 여기서는 고려하지 않는다.
        private UnassignedReason reasonFor(int p, List<Draft> tables) {
            boolean fitsButFull = false;
            boolean blockedOnly = false;
            for (Draft t : tables) {
                boolean ageOk = ageGap(concat(t.members, List.of(p))) <= rules.maxAgeGap();
                boolean pairOk = t.members.stream().noneMatch(m -> blocked[m][p]);
                if (ageOk && pairOk) {
                    fitsButFull = true;
                } else if (ageOk) {
                    blockedOnly = true;
                }
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

        List<HardRule> violations(List<Integer> group) {
            List<HardRule> result = new ArrayList<>();
            if (group.size() < rules.tableSizeMin() || group.size() > rules.tableSizeMax()) {
                result.add(HardRule.TABLE_SIZE);
            }
            if (ageGap(group) > rules.maxAgeGap()) {
                result.add(HardRule.AGE_GAP);
            }
            boolean blockedPair = IntStream.range(0, group.size()).anyMatch(i ->
                    IntStream.range(i + 1, group.size()).anyMatch(j -> blocked[group.get(i)][group.get(j)]));
            if (blockedPair) {
                result.add(HardRule.BLOCKED_PAIR);
            }
            return result;
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

        private double scoreValue(List<Integer> group) {
            return score(group).value().doubleValue();
        }

        Score score(List<Integer> group) {
            Parts parts = parts(group);
            Map<String, Double> penalties = new LinkedHashMap<>();
            penalties.put("allSameGender", parts.allSameGender());
            penalties.put("singleMinorityGender", parts.singleMinorityGender());
            penalties.put("metBefore", parts.metBefore());
            penalties.put("sameJobCategory", parts.sameJobCategory());
            penalties.put("sameMbti", parts.sameMbti());
            return new Score(BigDecimal.valueOf(parts.value()).setScale(4, RoundingMode.HALF_UP),
                    new ScoreDetail(round(parts.pairAvg()), round(parts.pairMin()), penalties));
        }

        // 반올림하지 않은 그룹 점수. 국소 탐색은 BigDecimal 없이 이걸로 비교한다.
        private double rawScore(List<Integer> group) {
            return parts(group).value();
        }

        private record Parts(double pairAvg, double pairMin, double allSameGender, double singleMinorityGender,
                             double metBefore, double sameJobCategory, double sameMbti) {
            double value() {
                double penalty = DoubleStream.of(allSameGender, singleMinorityGender, metBefore, sameJobCategory, sameMbti)
                        .sum();
                return Math.max(0, AVG_WEIGHT * pairAvg + MIN_WEIGHT * pairMin - penalty);
            }
        }

        // 그룹 점수 = 0.7·pairAvg + 0.3·pairMin − 페널티. score()와 rawScore()가 같이 쓴다.
        private Parts parts(List<Integer> group) {
            int size = group.size();
            int[] members = new int[size];
            for (int i = 0; i < size; i++) {
                members[i] = group.get(i);
            }
            double sum = 0;
            double pairMin = Double.MAX_VALUE;
            int metPairs = 0;
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    double value = pair[members[i]][members[j]];
                    sum += value;
                    pairMin = Math.min(pairMin, value);
                    if (met[members[i]][members[j]]) {
                        metPairs++;
                    }
                }
            }
            int pairs = size * (size - 1) / 2;
            double pairAvg = pairs == 0 ? 0 : sum / pairs;
            pairMin = pairs == 0 ? 0 : pairMin;

            boolean gendersKnown = true;
            for (int member : members) {
                gendersKnown &= genderId[member] >= 0;
            }
            int genderKinds = 0;
            boolean singleGender = false;
            for (int count : counts(members, genderId)) {
                genderKinds += count > 0 ? 1 : 0;
                singleGender |= count == 1;
            }
            return new Parts(pairAvg, pairMin,
                    gendersKnown && size > 1 && genderKinds == 1 ? PENALTY_ALL_SAME_GENDER : 0,
                    gendersKnown && genderKinds > 1 && singleGender ? PENALTY_SINGLE_MINORITY_GENDER : 0,
                    metPairs == 0 ? 0 : round(metPairs * PENALTY_MET_BEFORE_PER_PAIR),
                    max(counts(members, jobId)) >= CROWD_SIZE ? PENALTY_SAME_JOB_CATEGORY : 0,
                    max(counts(members, mbtiId)) >= CROWD_SIZE ? PENALTY_SAME_MBTI : 0);
        }

        // 값(ID)별 인원(값이 나온 순서). 모름(−1)은 세지 않는다.
        private static int[] counts(int[] members, int[] ids) {
            int[] counts = new int[members.length];
            int[] seen = new int[members.length];
            int kinds = 0;
            for (int member : members) {
                int id = ids[member];
                if (id < 0) {
                    continue;
                }
                int k = 0;
                while (k < kinds && seen[k] != id) {
                    k++;
                }
                if (k == kinds) {
                    seen[kinds++] = id;
                }
                counts[k]++;
            }
            return counts;
        }

        private static int max(int[] values) {
            int max = 0;
            for (int value : values) {
                max = Math.max(max, value);
            }
            return max;
        }

        private static int[] ids(List<Applicant> people, Function<Applicant, String> key) {
            Map<String, Integer> index = new HashMap<>();
            return people.stream().mapToInt(a -> {
                String value = key.apply(a);
                return value == null ? -1 : index.computeIfAbsent(value, v -> index.size());
            }).toArray();
        }

        // 멤버 전원(입력 순서)의 쌍별 점수, 그룹 점수, 항목별 쌍 평균. 값은 pairScore·score와 같은 계산이다.
        Explanation explain() {
            List<PairScore> pairs = new ArrayList<>();
            double[] sums = new double[CRITERIA.size()];
            int[] counts = new int[CRITERIA.size()];
            for (int i = 0; i < people.size(); i++) {
                for (int j = i + 1; j < people.size(); j++) {
                    Double[] items = items(people.get(i), people.get(j));
                    Map<String, Double> components = new LinkedHashMap<>();
                    for (int k = 0; k < items.length; k++) {
                        if (items[k] != null) {
                            components.put(CRITERIA.get(k), round(items[k]));
                            sums[k] += items[k];
                            counts[k]++;
                        }
                    }
                    pairs.add(new PairScore(people.get(i).applicationId(), people.get(j).applicationId(),
                            round(pair[i][j]), components));
                }
            }
            Map<String, Double> criteriaAvg = new LinkedHashMap<>();
            for (int k = 0; k < sums.length; k++) {
                if (counts[k] > 0) {
                    criteriaAvg.put(CRITERIA.get(k), round(sums[k] / counts[k]));
                }
            }
            return new Explanation(pairs, score(IntStream.range(0, people.size()).boxed().toList()), criteriaAvg);
        }

        private double pairScore(Applicant a, Applicant b) {
            MatchingWeights w = rules.weights();
            double[] weights = {w.getGender(), w.getMbti(), w.getInterests(), w.getWantedStyle(), w.getCustom()};
            Double[] items = items(a, b);
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

        // 페어 점수 항목 값(CRITERIA 순). 답이 없어 계산할 수 없는 항목은 null.
        private Double[] items(Applicant a, Applicant b) {
            return new Double[]{
                    a.gender() != null && b.gender() != null ? (a.gender().equals(b.gender()) ? 0.0 : 1.0) : null,
                    mbtiCompatibility(a.mbti(), b.mbti()),
                    jaccard(a.interests(), b.interests()),
                    average(wantedMatch(a, b), wantedMatch(b, a)),
                    customScore(a, b)
            };
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
    }

    // 관계(blocked·metBefore·again)는 한 방향만 있어도 양방향으로 본다. 장점 문구의 이전 만남 판정도 쓴다.
    static boolean related(Map<UUID, Set<UUID>> relation, Applicant a, Applicant b) {
        if (a.userId() == null || b.userId() == null) {
            return false;
        }
        return relation.getOrDefault(a.userId(), Set.of()).contains(b.userId())
                || relation.getOrDefault(b.userId(), Set.of()).contains(a.userId());
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
