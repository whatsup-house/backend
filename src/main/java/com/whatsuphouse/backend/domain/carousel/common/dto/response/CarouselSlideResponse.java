package com.whatsuphouse.backend.domain.carousel.common.dto.response;

import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CarouselSlideResponse {

    @Schema(description = "슬라이드 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "슬라이드 유형", example = "GATHERING")
    private SlideType type;

    @Schema(description = "슬라이드 제목", example = "봄 나들이 모임")
    private String title;

    @Schema(description = "슬라이드 내용 (nullable)", example = "함께 봄꽃 구경 가요!")
    private String content;

    @Schema(description = "이미지 URL", example = "https://cdn.example.com/images/slide1.jpg")
    private String imageUrl;

    @Schema(description = "연결된 모임 ID (nullable)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID gatheringId;

    @Schema(description = "모임 날짜 레이블, GATHERING 타입인 경우에만 존재 (nullable)", example = "2026-06-15")
    private String dateLabel;

    @Schema(description = "연결된 모임의 유효 상태. 과거 모집중 모임은 COMPLETED로 보정 (nullable)", example = "OPEN")
    private GatheringStatus gatheringStatus;

    @Schema(description = "정렬 순서", example = "1")
    private int sortOrder;

    public static CarouselSlideResponse from(CarouselSlide slide) {
        String dateLabel = (slide.getType() == SlideType.GATHERING && slide.getGathering() != null)
                ? slide.getGathering().getEventDate().toString()
                : null;

        UUID gatheringId = slide.getGathering() != null
                ? slide.getGathering().getId()
                : null;

        // 완료/취소된 모임 슬라이드에 모집중 뱃지가 붙지 않도록 유효 상태를 내려준다. (KAN-211)
        GatheringStatus gatheringStatus = slide.getGathering() != null
                ? slide.getGathering().getEffectiveStatus()
                : null;

        return CarouselSlideResponse.builder()
                .id(slide.getId())
                .type(slide.getType())
                .title(slide.getTitle())
                .content(slide.getContent())
                .imageUrl(slide.getImageUrl())
                .gatheringId(gatheringId)
                .dateLabel(dateLabel)
                .gatheringStatus(gatheringStatus)
                .sortOrder(slide.getSortOrder())
                .build();
    }
}
