package com.whatsuphouse.backend.domain.feed.client.dto.response;

import com.whatsuphouse.backend.domain.feed.common.dto.response.FeedGatheringResponse;
import com.whatsuphouse.backend.domain.feed.entity.FeedMedia;
import com.whatsuphouse.backend.domain.feed.entity.FeedPost;
import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 공개 피드 항목. 피드 게시물(POST) 또는 포토후기(REVIEW). (KAN-380) */
@Getter
@Builder
public class FeedItemResponse {

    public enum Kind { POST, REVIEW }

    @Schema(description = "항목 종류", example = "POST")
    private Kind kind;

    @Schema(description = "항목 ID (POST=게시물 ID, REVIEW=후기 ID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "미디어 목록 (노출 순서)")
    private List<FeedMedia> media;

    @Schema(description = "본문 (nullable)", example = "지난 주말 와인 나잇")
    private String caption;

    @Schema(description = "게시 시각 (POST=인스타 게시 시각, REVIEW=후기 작성 시각)", example = "2026-10-01T19:30:00")
    private LocalDateTime postedAt;

    @Schema(description = "연결된 게더링 (nullable)")
    private FeedGatheringResponse gathering;

    @Schema(description = "인스타그램 원문 URL (POST만, nullable)", example = "https://www.instagram.com/p/abc123/")
    private String instagramUrl;

    @Schema(description = "후기 ID (REVIEW만, nullable)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID reviewId;

    public static FeedItemResponse from(FeedPost post) {
        return FeedItemResponse.builder()
                .kind(Kind.POST)
                .id(post.getId())
                .media(post.getMedia())
                .caption(post.getCaption())
                .postedAt(post.getPostedAt())
                .gathering(FeedGatheringResponse.from(post.getGathering()))
                .instagramUrl(post.getInstagramUrl())
                .build();
    }

    // images: 해당 후기 이미지(display_order 순).
    public static FeedItemResponse of(Review review, List<ReviewImage> images) {
        return FeedItemResponse.builder()
                .kind(Kind.REVIEW)
                .id(review.getId())
                .media(images.stream().map(image -> FeedMedia.image(image.getImageUrl())).toList())
                .caption(review.getReviewContent())
                .postedAt(review.getCreatedAt())
                .gathering(FeedGatheringResponse.from(review.getGathering()))
                .reviewId(review.getId())
                .build();
    }
}
