package com.whatsuphouse.backend.domain.gathering.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** 모임 종류 상세 + 회차 목록. (KAN-338) */
@Getter
@Builder
public class GatheringDetailResponse {

    @Schema(description = "모임 종류 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "제목 (Accept-Language 번역 적용)", example = "재즈 게더링")
    private String title;

    @Schema(description = "소개 (Accept-Language 번역 적용)", example = "편안하게 대화 나누는 소규모 모임입니다.")
    private String description;

    @Schema(description = "진행 방식", example = "[\"아이스브레이킹\", \"식사와 대화\"]")
    private List<String> howToRun;

    @Schema(description = "태그", example = "[\"취미\", \"2030\"]")
    private List<String> tags;

    @Schema(description = "썸네일 URL", example = "https://cdn.example.com/gathering/thumb.jpg")
    private String thumbnailUrl;

    @Schema(description = "상세 사진 URL 목록 (순서 = 노출 순서, 없으면 []). 상세 슬라이더는 썸네일 + 이 목록",
            example = "[\"https://cdn.example.com/gathering/detail-1.jpg\"]")
    private List<String> imageUrls;

    @Schema(description = "REGULAR(일반) / RANDOM_TABLE(우연한 식탁)", example = "REGULAR")
    private GatheringType gatheringType;

    @Schema(description = "기본 가격. 회차 가격 오버라이드가 없을 때 쓴다", example = "15000")
    private Integer basePrice;

    @Schema(description = "회차 목록 (날짜·시작 시간 순)")
    private List<GatheringSessionResponse> sessions;

    public static GatheringDetailResponse of(Gathering gathering, String title, String description,
                                             List<GatheringSessionResponse> sessions) {
        return GatheringDetailResponse.builder()
                .id(gathering.getId())
                .title(title)
                .description(description)
                .howToRun(gathering.getHowToRun())
                .tags(gathering.getTags())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .imageUrls(gathering.getImageUrls())
                .gatheringType(gathering.getGatheringType())
                .basePrice(gathering.getBasePrice())
                .sessions(sessions)
                .build();
    }
}
