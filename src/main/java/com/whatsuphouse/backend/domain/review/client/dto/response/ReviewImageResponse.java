package com.whatsuphouse.backend.domain.review.client.dto.response;

import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ReviewImageResponse {

    private UUID imageId;
    private String imageUrl;
    private Integer displayOrder;

    public static ReviewImageResponse from(ReviewImage image) {
        return ReviewImageResponse.builder()
                .imageId(image.getId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }
}
