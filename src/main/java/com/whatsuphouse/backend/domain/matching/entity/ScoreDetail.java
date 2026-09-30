package com.whatsuphouse.backend.domain.matching.entity;

import java.util.Map;

/** 그룹 점수 내역. dining_tables.score_detail(JSONB)에 그대로 저장돼 어드민에 노출된다. (설계 4.4) */
public record ScoreDetail(double pairAvg, double pairMin, Map<String, Double> penalties) {
}
