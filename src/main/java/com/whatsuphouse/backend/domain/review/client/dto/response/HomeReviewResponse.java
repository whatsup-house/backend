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
                // 기존 API 호환: 후기를 쓴 신청의 회차 ID(= 옛 게더링 ID). (KAN-337)
                .gatheringId(review.getApplication().getSession().getId())
                .gatheringTitle(review.getGathering().getTitle())
                .reviewContent(review.getReviewContent())
                .likeCount(review.getLikeCount())
                .thumbnailImageUrl(images.isEmpty() ? null : images.get(0).getImageUrl())
                .homeDisplayOrder(review.getHomeDisplayOrder())
                .build();
    }
}
