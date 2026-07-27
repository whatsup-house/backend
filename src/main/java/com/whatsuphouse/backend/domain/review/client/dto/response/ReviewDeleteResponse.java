package com.whatsuphouse.backend.domain.review.client.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ReviewDeleteResponse {

    private UUID reviewId;
    private boolean deleted;

    public static ReviewDeleteResponse of(UUID reviewId) {
        return ReviewDeleteResponse.builder()
                .reviewId(reviewId)
                .deleted(true)
                .build();
    }
}
