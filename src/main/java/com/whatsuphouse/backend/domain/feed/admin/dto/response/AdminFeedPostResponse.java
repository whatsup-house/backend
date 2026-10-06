package com.whatsuphouse.backend.domain.feed.admin.dto.response;

import com.whatsuphouse.backend.domain.feed.common.dto.response.FeedGatheringResponse;
import com.whatsuphouse.backend.domain.feed.entity.FeedMedia;
import com.whatsuphouse.backend.domain.feed.entity.FeedPost;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminFeedPostResponse {

    @Schema(description = "피드 게시물 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "미디어 목록 (노출 순서)")
    private List<FeedMedia> media;

    @Schema(description = "본문 (nullable)", example = "지난 주말 와인 나잇 현장")
    private String caption;

    @Schema(description = "인스타그램 원문 URL (nullable)", example = "https://www.instagram.com/p/abc123/")
    private String instagramUrl;

    @Schema(description = "인스타그램 게시 시각", example = "2026-10-01T19:30:00")
    private LocalDateTime postedAt;

    @Schema(description = "연결된 게더링 (nullable)")
    private FeedGatheringResponse gathering;

    @Schema(description = "노출 여부", example = "true")
    private boolean visible;

    @Schema(description = "생성 일시", example = "2026-10-02T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2026-10-02T10:00:00")
    private LocalDateTime updatedAt;

    public static AdminFeedPostResponse from(FeedPost post) {
        return AdminFeedPostResponse.builder()
                .id(post.getId())
                .media(post.getMedia())
                .caption(post.getCaption())
                .instagramUrl(post.getInstagramUrl())
                .postedAt(post.getPostedAt())
                .gathering(FeedGatheringResponse.from(post.getGathering()))
                .visible(post.isVisible())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
