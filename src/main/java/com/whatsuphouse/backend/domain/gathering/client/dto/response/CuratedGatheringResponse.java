package com.whatsuphouse.backend.domain.gathering.client.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
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

    // 큐레이션은 종류 단위지만 기존 API 호환상 id는 상세로 열 대표 회차 ID다.
    // (마이그레이션된 종류 ID는 가장 오래된 회차 ID와 같아 종류 ID로 내리면 지난 회차가 열린다.) (KAN-337)
    public static CuratedGatheringResponse from(Gathering gathering, GatheringSession session) {
        String locationName = session.getLocation() != null
                ? session.getLocation().getName()
                : null;

        return CuratedGatheringResponse.builder()
                .id(session.getId())
                .title(gathering.getTitle())
                .thumbnailUrl(gathering.getThumbnailUrl())
                .eventDate(session.getEventDate())
                .locationName(locationName)
                .price(session.getEffectivePrice())
                .status(session.getEffectiveStatus())
                .curatedRank(gathering.getCuratedRank())
                .build();
    }
}
