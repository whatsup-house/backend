package com.whatsuphouse.backend.domain.feed.common.dto.response;

import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** 피드 항목·관리자 응답에 붙는 연결 게더링 요약. (KAN-380) */
@Getter
@AllArgsConstructor
public class FeedGatheringResponse {

    @Schema(description = "게더링 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "게더링 제목", example = "와썹 와인 나잇")
    private String title;

    public static FeedGatheringResponse from(Gathering gathering) {
        return gathering == null ? null : new FeedGatheringResponse(gathering.getId(), gathering.getTitle());
    }
}
