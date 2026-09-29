package com.whatsuphouse.backend.domain.review.client.dto.response;

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
public class ReviewResponse {

    private UUID reviewId;
    private UUID userId;
    private String nickname;
    private UUID applicationId;
    private UUID gatheringId;
    private String gatheringTitle;
    private ReviewType reviewType;
    private String reviewContent;
    private Integer likeCount;
    private List<ReviewImageResponse> images;
    private LocalDateTime createdAt;

    public static ReviewResponse of(Review review, List<ReviewImage> images) {
        return ReviewResponse.builder()
                .reviewId(review.getId())
                .userId(review.getUser().getId())
                .nickname(review.getUser().getNickname())
                .applicationId(review.getApplication().getId())
                // 기존 API 호환: 후기를 쓴 신청의 회차 ID(= 옛 게더링 ID). (KAN-337)
                .gatheringId(review.getApplication().getSession().getId())
                .gatheringTitle(review.getGathering().getTitle())
                .reviewType(review.getReviewType())
                .reviewContent(review.getReviewContent())
                .likeCount(review.getLikeCount())
                .images(images.stream().map(ReviewImageResponse::from).toList())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
