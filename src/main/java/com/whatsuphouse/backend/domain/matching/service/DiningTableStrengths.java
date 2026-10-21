package com.whatsuphouse.backend.domain.matching.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 테이블 장점 문구 규칙(설계 6.2, KAN-432). 참가자 테이블 상세·관리자 근거·확정 채팅 공용. AI 없이 엔진 점수로 만든다.
 * criteriaAvg에서 0.5 이상인 항목을 높은 순으로 최대 2개(동점은 PRIORITY 순), % = 반올림(값×100).
 * 하나도 없으면 이전 만남 여부로 기본 문구 1개. 결과는 항상 1개 이상이다.
 */
final class DiningTableStrengths {

    static final String ALL_FIRST_MEET = "모두 처음 만나는 사람들로 꾸린 테이블이에요!";
    static final String NEW_PEOPLE = "새로운 사람들과 만나는 테이블이에요!";
    private static final double THRESHOLD = 0.5;
    private static final int MAX_COUNT = 2;
    private static final List<String> PRIORITY = List.of("wantedStyle", "interests", "mbti", "gender", "custom");

    private DiningTableStrengths() {
    }

    static List<String> describe(MatchingEngine.Explanation explanation, List<MatchingEngine.Applicant> members,
                                 MatchingEngine.Relations relations) {
        Map<String, Double> avg = explanation.criteriaAvg();
        List<String> strengths = PRIORITY.stream()
                .filter(key -> avg.containsKey(key) && avg.get(key) >= THRESHOLD)
                .sorted(Comparator.comparingDouble((String key) -> avg.get(key)).reversed())
                .limit(MAX_COUNT)
                .map(key -> phrase(key, avg.get(key), members))
                .toList();
        if (!strengths.isEmpty()) {
            return strengths;
        }
        return List.of(anyMetBefore(members, relations) ? NEW_PEOPLE : ALL_FIRST_MEET);
    }

    private static String phrase(String key, double value, List<MatchingEngine.Applicant> members) {
        long percent = Math.round(value * 100);
        return switch (key) {
            case "wantedStyle" -> "대화 스타일 " + percent + "% 일치하는 테이블이에요!";
            case "interests" -> commonInterest(members)
                    .map(interest -> "'" + interest + "'" + objectParticle(interest) + " 함께 좋아하는 테이블이에요!")
                    .orElse("관심사가 " + percent + "% 겹치는 테이블이에요!");
            case "mbti" -> "MBTI 궁합 " + percent + "% 테이블이에요!";
            case "gender" -> "성비가 고르게 맞춰진 테이블이에요!";
            default -> "취향이 " + percent + "% 맞는 테이블이에요!";
        };
    }

    // 멤버 전원이 고른 관심사 중 첫 멤버의 답 순서로 첫 번째. 한 명이라도 답이 없으면 없음.
    private static Optional<String> commonInterest(List<MatchingEngine.Applicant> members) {
        if (members.isEmpty()) {
            return Optional.empty();
        }
        return members.get(0).interests().stream()
                .filter(interest -> members.stream().allMatch(member -> member.interests().contains(interest)))
                .findFirst();
    }

    // 마지막 글자에 받침이 있으면 '을', 없거나 한글이 아니면 '를'
    private static String objectParticle(String word) {
        char last = word.isEmpty() ? ' ' : word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0 ? "을" : "를";
    }

    // 이 테이블 밖에서 같이 앉은 적 있는 쌍(다시 만나고 싶다고 한 쌍 포함)이 있는지
    private static boolean anyMetBefore(List<MatchingEngine.Applicant> members, MatchingEngine.Relations relations) {
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (MatchingEngine.related(relations.metBefore(), members.get(i), members.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }
}
