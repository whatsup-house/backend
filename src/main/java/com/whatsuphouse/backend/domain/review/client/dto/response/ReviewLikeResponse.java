package com.whatsuphouse.backend.domain.review.client.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ReviewLikeResponse {

    private UUID reviewId;
    private boolean liked;
    private Integer likeCount;

    public static ReviewLikeResponse of(UUID reviewId, boolean liked, Integer likeCount) {
        return ReviewLikeResponse.builder()
                .reviewId(reviewId)
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }
}
