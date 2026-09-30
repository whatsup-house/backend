package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 우연한 식탁(RANDOM_TABLE) 자동매칭 rule-v1 엔진.
 * - 하드 조건 없음. age/budget/available_dates 매직 키 분기는 삭제했다. (KAN-341, 표준 질문 기반 v2는 KAN-345)
 * - Pair score: SAME(같으면 +1) / DIVERSE(다르면 +1) / OVERLAP(Jaccard), 질문 가중치 가중평균
 * - Group score: avg × 0.7 + min × 0.3, 다양성 보정 감점
 * - 그룹 묶기: greedy(남은 순서대로 seed → best 멤버 추가), groupSize 미달 잔여 인원은 미배정으로 남김
 */
@Component
public class MatchingEngine {

    public static final int DEFAULT_GROUP_SIZE = 4;
    private static final double AVG_WEIGHT = 0.7;
    private static final double MIN_WEIGHT = 0.3;

    private static final String KEY_GENDER = "gender";
    private static final String KEY_JOB = "job_category";
    private static final String KEY_MBTI = "mbti";

    public record Applicant(UUID applicationId, Map<String, Object> answers) {}

    public record MatchingField(String questionKey, MatchingStrategy strategy, double weight) {}

    public record GroupResult(List<UUID> applicationIds, BigDecimal score) {}

    // groupSize: 그룹당 인원 수(관리자 지정, 기본 DEFAULT_GROUP_SIZE). 잔여 인원은 미배정으로 남긴다. (KAN-224)
    public List<GroupResult> match(List<Applicant> applicants, List<MatchingField> fields, int groupSize) {
        List<Applicant> pool = new ArrayList<>(applicants);
        List<GroupResult> results = new ArrayList<>();

        while (pool.size() >= groupSize) {
            List<Applicant> group = new ArrayList<>();
            group.add(pool.get(0));

            while (group.size() < groupSize) {
                Applicant best = null;
                double bestScore = -1;
                for (Applicant c : pool) {
                    if (group.contains(c)) continue;
                    double s = partialAvgPairScore(group, c, fields);
                    if (s > bestScore) {
                        bestScore = s;
                        best = c;
                    }
                }
                group.add(best);
            }

            results.add(new GroupResult(
                    group.stream().map(Applicant::applicationId).toList(), groupScore(group, fields)));
            pool.removeAll(group);
        }
        return results;
    }

    public BigDecimal scoreGroup(List<Applicant> group, List<MatchingField> fields) {
        return groupScore(group, fields);
    }

    // ── Pair score ──────────────────────────────────────────────────────────

    private double partialAvgPairScore(List<Applicant> group, Applicant candidate, List<MatchingField> fields) {
        double sum = 0;
        int count = 0;
        for (Applicant m : group) {
            sum += pairScore(m, candidate, fields);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private double pairScore(Applicant a, Applicant b, List<MatchingField> fields) {
        double weightedSum = 0;
        double totalWeight = 0;
        for (MatchingField f : fields) {
            Double s = scoreFor(f, a, b);
            if (s != null) {
                weightedSum += s * f.weight();
                totalWeight += f.weight();
            }
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }

    private Double scoreFor(MatchingField f, Applicant a, Applicant b) {
        String key = f.questionKey();
        return switch (f.strategy()) {
            case SAME -> sameScore(a, b, key);
            case DIVERSE -> diverseScore(a, b, key);
            case OVERLAP -> overlapScore(a, b, key);
        };
    }

    private Double sameScore(Applicant a, Applicant b, String key) {
        Double diverse = diverseScore(a, b, key);
        return diverse == null ? null : 1.0 - diverse;
    }

    private Double diverseScore(Applicant a, Applicant b, String key) {
        String va = stringValue(a, key), vb = stringValue(b, key);
        if (va == null || vb == null) return null;
        return va.equals(vb) ? 0.0 : 1.0;
    }

    private Double overlapScore(Applicant a, Applicant b, String key) {
        Set<String> sa = listValue(a, key), sb = listValue(b, key);
        if (sa.isEmpty() && sb.isEmpty()) return null;
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    // ── Group score + 다양성 보정 ────────────────────────────────────────────

    private BigDecimal groupScore(List<Applicant> group, List<MatchingField> fields) {
        List<Double> pairs = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                pairs.add(pairScore(group.get(i), group.get(j), fields));
            }
        }
        double avg = pairs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = pairs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double base = avg * AVG_WEIGHT + min * MIN_WEIGHT;

        double penalty = diversityPenalty(group);
        double score = Math.max(0, base - penalty);
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    private double diversityPenalty(List<Applicant> group) {
        double penalty = 0;
        // 성별 쏠림
        long male = group.stream().map(a -> stringValue(a, KEY_GENDER)).filter("MALE"::equals).count();
        long known = group.stream().map(a -> stringValue(a, KEY_GENDER)).filter(v -> v != null).count();
        // 그룹 인원 기준으로 성별 쏠림을 평가한다(가변 그룹 크기 대응, 4인 점수는 불변). (KAN-224)
        if (known == group.size()) {
            long female = known - male;
            if (male == 0 || female == 0) penalty += 0.30;          // 전원 동성
            else if (male == 1 || female == 1) penalty += 0.10;     // 1명만 소수 성별
        }
        // 동일 직군 3명 이상
        if (maxSameCount(group, KEY_JOB) >= 3) penalty += 0.05;
        // 동일 MBTI 3명 이상
        if (maxSameCount(group, KEY_MBTI) >= 3) penalty += 0.05;
        return penalty;
    }

    private long maxSameCount(List<Applicant> group, String key) {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (Applicant a : group) {
            String v = stringValue(a, key);
            if (v != null) counts.merge(v, 1L, Long::sum);
        }
        return counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    // ── 답변 값 추출 헬퍼 ──────────────────────────────────────────────────────

    private String stringValue(Applicant a, String key) {
        Object v = a.answers().get(key);
        return v instanceof String s ? s : (v != null ? v.toString() : null);
    }

    private Set<String> listValue(Applicant a, String key) {
        Object v = a.answers().get(key);
        Set<String> result = new LinkedHashSet<>();
        if (v instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null) result.add(o.toString());
            }
        } else if (v instanceof String s && !s.isBlank()) {
            result.add(s);
        }
        return result;
    }
}
