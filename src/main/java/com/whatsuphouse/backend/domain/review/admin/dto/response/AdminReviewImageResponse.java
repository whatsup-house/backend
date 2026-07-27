package com.whatsuphouse.backend.domain.review.admin.dto.response;

import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AdminReviewImageResponse {

    private UUID imageId;
    private String imageUrl;
    private Integer displayOrder;

    public static AdminReviewImageResponse from(ReviewImage image) {
        return AdminReviewImageResponse.builder()
                .imageId(image.getId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }
}
