package com.whatsuphouse.backend.domain.gathering.client.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class CuratedGatheringResponse {

    @Schema(description = "게더링 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "제목", example = "4월 홍대 소셜 게더링")
    private String title;

    @Schema(description = "썸네일 URL", example = "https://cdn.example.com/gathering/thumb.jpg")
    private String thumbnailUrl;

    @Schema(description = "행사 날짜", example = "2026-05-10")
    private LocalDate eventDate;

    @Schema(description = "장소명", example = "카페 재즈")
    private String locationName;

    @Schema(description = "가격", example = "15000")
    private Integer price;

    @Schema(description = "게더링 상태", example = "OPEN")
    private GatheringStatus status;

    @Schema(description = "큐레이션 순위 (0부터 시작)", example = "0")
    private int curatedRank;

    public static CuratedGatheringResponse from(Gathering gathering) {
        String locationName = gathering.getLocation() != null
                ? gathering.getLocation().getName()
                : null;

        return CuratedGatheringResponse.builder()
                .id(gathering.getId())
                .title(gathering.getTitle())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .eventDate(gathering.getEventDate())
                .locationName(locationName)
                .price(gathering.getPrice())
                .status(gathering.getEffectiveStatus())
                .curatedRank(gathering.getCuratedRank())
                .build();
    }
}
