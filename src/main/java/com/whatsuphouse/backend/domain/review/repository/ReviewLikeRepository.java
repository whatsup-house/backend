package com.whatsuphouse.backend.domain.review.repository;

import com.whatsuphouse.backend.domain.review.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, UUID> {

    Optional<ReviewLike> findByReviewIdAndUserId(UUID reviewId, UUID userId);

    boolean existsByReviewIdAndUserId(UUID reviewId, UUID userId);

    long countByReviewId(UUID reviewId);
}
