package com.whatsuphouse.backend.domain.review.repository;

import com.whatsuphouse.backend.domain.review.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, UUID> {

    List<ReviewImage> findByReviewIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID reviewId);

    List<ReviewImage> findByReviewIdInAndDeletedAtIsNullOrderByDisplayOrderAsc(List<UUID> reviewIds);
}
