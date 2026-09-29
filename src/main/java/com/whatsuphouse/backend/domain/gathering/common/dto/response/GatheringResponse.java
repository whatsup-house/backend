package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 모임 목록 항목: 종류 + 조건에 맞는 회차 요약. (KAN-338) */
@Getter
@Builder
public class GatheringResponse {

    @Schema(description = "모임 종류 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "제목", example = "재즈 게더링")
    private String title;

    @Schema(description = "소개", example = "편안하게 대화 나누는 소규모 모임입니다.")
    private String description;

    @Schema(description = "태그", example = "[\"취미\", \"2030\"]")
    private List<String> tags;

    @Schema(description = "썸네일 URL", example = "https://cdn.example.com/gathering/thumb.jpg")
    private String thumbnailUrl;

    @Schema(description = "REGULAR(일반) / RANDOM_TABLE(우연한 식탁)", example = "REGULAR")
    private GatheringType gatheringType;

    @Schema(description = "기본 가격. 회차 가격 오버라이드가 없을 때 쓴다", example = "15000")
    private Integer basePrice;

    // 목록 정렬(최신순/오래된순)용. (KAN-295)
    @Schema(description = "종류 등록일", example = "2026-09-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "조건에 맞는 회차 (날짜·시작 시간 순). 조건이 없으면 오늘 이후 회차")
    private List<GatheringSessionResponse> sessions;

    public static GatheringResponse of(Gathering gathering, List<GatheringSessionResponse> sessions) {
        return GatheringResponse.builder()
                .id(gathering.getId())
                .title(gathering.getTitle())
                .description(gathering.getDescription())
                .tags(gathering.getTags())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .gatheringType(gathering.getGatheringType())
                .basePrice(gathering.getBasePrice())
                .createdAt(gathering.getCreatedAt())
                .sessions(sessions)
                .build();
    }
}
