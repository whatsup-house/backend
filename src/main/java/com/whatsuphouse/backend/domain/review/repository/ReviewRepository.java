package com.whatsuphouse.backend.domain.review.repository;

import com.whatsuphouse.backend.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByApplicationIdAndDeletedAtIsNull(UUID applicationId);

    Page<Review> findByGatheringIdAndDeletedAtIsNull(UUID gatheringId, Pageable pageable);

    Page<Review> findByDeletedAtIsNull(Pageable pageable);

    Page<Review> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Review> findByIsHomeFeaturedAndDeletedAtIsNull(Boolean isHomeFeatured, Pageable pageable);

    List<Review> findAllByIdInAndDeletedAtIsNull(List<UUID> reviewIds);

    List<Review> findByIsHomeFeaturedTrueAndDeletedAtIsNullOrderByHomeDisplayOrderAscCreatedAtDesc();
}
