package com.whatsuphouse.backend.domain.review.client.dto.response;

import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class HomeReviewResponse {

    private UUID reviewId;
    private String nickname;
    private UUID gatheringId;
    private String gatheringTitle;
    private String reviewContent;
    private Integer likeCount;
    private String thumbnailImageUrl;
    private Integer homeDisplayOrder;

    public static HomeReviewResponse of(Review review, List<ReviewImage> images) {
        return HomeReviewResponse.builder()
                .reviewId(review.getId())
                .nickname(review.getUser().getNickname())
                .gatheringId(review.getGathering().getId())
                .gatheringTitle(review.getGathering().getTitle())
                .reviewContent(review.getReviewContent())
                .likeCount(review.getLikeCount())
                .thumbnailImageUrl(images.isEmpty() ? null : images.get(0).getImageUrl())
                .homeDisplayOrder(review.getHomeDisplayOrder())
                .build();
    }
}
