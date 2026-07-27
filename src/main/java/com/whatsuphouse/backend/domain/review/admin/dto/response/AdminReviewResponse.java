package com.whatsuphouse.backend.domain.review.admin.dto.response;

import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import com.whatsuphouse.backend.domain.review.enums.ReviewType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminReviewResponse {

    private UUID reviewId;
    private UUID userId;
    private String nickname;
    private UUID applicationId;
    private UUID gatheringId;
    private String gatheringTitle;
    private ReviewType reviewType;
    private String reviewContent;
    private Integer likeCount;
    private boolean isHomeFeatured;
    private Integer homeDisplayOrder;
    private List<AdminReviewImageResponse> images;
    private LocalDateTime createdAt;

    public static AdminReviewResponse of(Review review, List<ReviewImage> images) {
        return AdminReviewResponse.builder()
                .reviewId(review.getId())
                .userId(review.getUser().getId())
                .nickname(review.getUser().getNickname())
                .applicationId(review.getApplication().getId())
                .gatheringId(review.getGathering().getId())
                .gatheringTitle(review.getGathering().getTitle())
                .reviewType(review.getReviewType())
                .reviewContent(review.getReviewContent())
                .likeCount(review.getLikeCount())
                .isHomeFeatured(review.isHomeFeatured())
                .homeDisplayOrder(review.getHomeDisplayOrder())
                .images(images.stream().map(AdminReviewImageResponse::from).toList())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
