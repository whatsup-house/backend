package com.whatsuphouse.backend.domain.matching.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MatchingRunResponse {

    private UUID gatheringId;
    private String algorithmVersion;
    private int confirmedCount;   // 매칭 대상 CONFIRMED 신청 수
    private int groupCount;       // 생성된 추천 그룹 수
    private int matchedCount;     // 그룹에 배정된 신청 수
    private int unmatchedCount;   // 미배정 신청 수
}
