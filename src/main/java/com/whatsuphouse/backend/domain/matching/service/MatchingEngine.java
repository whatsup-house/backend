package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * - Hard condition: 나이 ±8 / 예산 겹침 / 참가 가능 날짜 겹침 (question_key 기준, 폼에 있으면 적용)
 * - Pair score: SAME(나이 근접도) / DIVERSE(다르면 +1) / OVERLAP(Jaccard), 질문 가중치 가중평균
 * - Group score: avg × 0.7 + min × 0.3, 다양성 보정 감점
 * - 그룹 묶기: greedy(배치 어려운 사람 우선 seed → best 멤버 추가), 4명 미달은 미배정으로 남김
 */
@Component
public class MatchingEngine {

    private static final int GROUP_SIZE = 4;
    private static final double AVG_WEIGHT = 0.7;
    private static final double MIN_WEIGHT = 0.3;
    private static final int AGE_LIMIT = 8;

    private static final String KEY_AGE = "age";
    private static final String KEY_BUDGET = "budget";
    private static final String KEY_DATES = "available_dates";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_JOB = "job_category";
    private static final String KEY_MBTI = "mbti";

    public record Applicant(UUID applicationId, Map<String, Object> answers) {}

    public record MatchingField(String questionKey, MatchingStrategy strategy, double weight) {}

    public record GroupResult(List<UUID> applicationIds, BigDecimal score, LocalDate eventDate) {}

    public List<GroupResult> match(List<Applicant> applicants, List<MatchingField> fields, LocalDate fallbackDate) {
        boolean datesUsed = applicants.stream().anyMatch(a -> !dates(a).isEmpty());

        List<Applicant> pool = new ArrayList<>(applicants);
        List<GroupResult> results = new ArrayList<>();

        while (pool.size() >= GROUP_SIZE) {
            Applicant seed = hardestToPlace(pool);
            List<Applicant> group = new ArrayList<>();
            group.add(seed);
            Set<String> groupDates = datesUsed ? new LinkedHashSet<>(dates(seed)) : null;

            while (group.size() < GROUP_SIZE) {
                Applicant best = null;
                double bestScore = -1;
                Set<String> bestDates = null;

                for (Applicant c : pool) {
                    if (group.contains(c)) continue;
                    if (!compatibleWithAll(c, group)) continue;

                    Set<String> nextDates = groupDates;
                    if (datesUsed) {
                        nextDates = intersect(groupDates, dates(c));
                        if (nextDates.isEmpty()) continue;
                    }
                    double s = partialAvgPairScore(group, c, fields);
                    if (s > bestScore) {
                        bestScore = s;
                        best = c;
                        bestDates = nextDates;
                    }
                }
                if (best == null) break;
                group.add(best);
                groupDates = bestDates;
            }

            if (group.size() == GROUP_SIZE) {
                BigDecimal score = groupScore(group, fields);
                LocalDate eventDate = pickEventDate(groupDates, fallbackDate);
                results.add(new GroupResult(
                        group.stream().map(Applicant::applicationId).toList(), score, eventDate));
                pool.removeAll(group);
            } else {
                // 4명을 못 채우는 seed는 미배정으로 남긴다 (관리자가 수동 처리)
                pool.remove(seed);
            }
        }
        return results;
    }

    // ── Hard condition ────────────────────────────────────────────────────────

    private boolean compatibleWithAll(Applicant c, List<Applicant> group) {
        for (Applicant m : group) {
            if (!hardCompatible(c, m)) return false;
        }
        return true;
    }

    private boolean hardCompatible(Applicant a, Applicant b) {
        Integer ageA = age(a), ageB = age(b);
        if (ageA != null && ageB != null && Math.abs(ageA - ageB) > AGE_LIMIT) return false;

        Set<String> budgetA = listValue(a, KEY_BUDGET), budgetB = listValue(b, KEY_BUDGET);
        if (!budgetA.isEmpty() && !budgetB.isEmpty() && disjoint(budgetA, budgetB)) return false;

        Set<String> dA = dates(a), dB = dates(b);
        if (!dA.isEmpty() && !dB.isEmpty() && disjoint(dA, dB)) return false;

        return true;
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
            case SAME -> KEY_AGE.equals(key) ? ageProximity(a, b) : null; // 예산/날짜 same은 hard condition 전용
            case DIVERSE -> diverseScore(a, b, key);
            case OVERLAP -> overlapScore(a, b, key);
        };
    }

    private Double ageProximity(Applicant a, Applicant b) {
        Integer ageA = age(a), ageB = age(b);
        if (ageA == null || ageB == null) return null;
        int d = Math.abs(ageA - ageB);
        if (d <= 3) return 1.0;
        if (d <= 5) return 0.5;
        if (d <= 8) return 0.2;
        return 0.0;
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
        if (known == GROUP_SIZE) {
            long female = known - male;
            if (male == 0 || female == 0) penalty += 0.30;          // 4명 동성
            else if (male == 1 || female == 1) penalty += 0.10;     // 3:1
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

    // ── 그룹 날짜 ──────────────────────────────────────────────────────────────

    private LocalDate pickEventDate(Set<String> groupDates, LocalDate fallback) {
        if (groupDates != null) {
            return groupDates.stream()
                    .map(this::parseDate)
                    .filter(d -> d != null)
                    .sorted()
                    .findFirst()
                    .orElse(fallback);
        }
        return fallback;
    }

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ── 답변 값 추출 헬퍼 ──────────────────────────────────────────────────────

    private Applicant hardestToPlace(List<Applicant> pool) {
        Applicant hardest = pool.get(0);
        int min = Integer.MAX_VALUE;
        for (Applicant a : pool) {
            int c = 0;
            for (Applicant b : pool) {
                if (a != b && hardCompatible(a, b)) c++;
            }
            if (c < min) {
                min = c;
                hardest = a;
            }
        }
        return hardest;
    }

    private Integer age(Applicant a) {
        Object v = a.answers().get(KEY_AGE);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

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

    private Set<String> dates(Applicant a) {
        return listValue(a, KEY_DATES);
    }

    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> r = new LinkedHashSet<>(a);
        r.retainAll(b);
        return r;
    }

    private boolean disjoint(Set<String> a, Set<String> b) {
        for (String s : a) {
            if (b.contains(s)) return false;
        }
        return true;
    }
}
