package com.whatsuphouse.backend.domain.review.repository;

import com.whatsuphouse.backend.domain.review.entity.Review;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByApplicationIdAndDeletedAtIsNull(UUID applicationId);

    Page<Review> findByGatheringIdAndDeletedAtIsNull(UUID gatheringId, Pageable pageable);

    @Query("""
            select r from Review r
            where r.deletedAt is null
              and r.gathering.title = :title
              and (
                    (r.gathering.gatheringType is null and :gatheringType is null)
                    or r.gathering.gatheringType = :gatheringType
                  )
            """)
    Page<Review> findByGatheringReviewGroupAndDeletedAtIsNull(
            String title, GatheringType gatheringType, Pageable pageable);

    Page<Review> findByDeletedAtIsNull(Pageable pageable);

    Page<Review> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Review> findByIsHomeFeaturedAndDeletedAtIsNull(Boolean isHomeFeatured, Pageable pageable);

    List<Review> findAllByIdInAndDeletedAtIsNull(List<UUID> reviewIds);

    List<Review> findByIsHomeFeaturedTrueAndDeletedAtIsNullOrderByHomeDisplayOrderAscCreatedAtDesc();

    // 리뷰 페이지 위치(locate) 계산용 — 대상 리뷰보다 정렬상 앞서는 리뷰 수를 센다.
    // LATEST(createdAt desc): 더 최신인 리뷰 수
    long countByDeletedAtIsNullAndCreatedAtAfter(LocalDateTime createdAt);

    // LIKES(likeCount desc, createdAt desc): 추천 수가 더 많은 리뷰 + 추천 수 같고 더 최신인 리뷰
    long countByDeletedAtIsNullAndLikeCountGreaterThan(Integer likeCount);

    long countByDeletedAtIsNullAndLikeCountAndCreatedAtAfter(Integer likeCount, LocalDateTime createdAt);

    long countByGatheringIdAndDeletedAtIsNullAndCreatedAtAfter(UUID gatheringId, LocalDateTime createdAt);

    long countByGatheringIdAndDeletedAtIsNullAndLikeCountGreaterThan(UUID gatheringId, Integer likeCount);

    long countByGatheringIdAndDeletedAtIsNullAndLikeCountAndCreatedAtAfter(UUID gatheringId, Integer likeCount, LocalDateTime createdAt);

    @Query("""
            select count(r) from Review r
            where r.deletedAt is null
              and r.createdAt > :createdAt
              and r.gathering.title = :title
              and (
                    (r.gathering.gatheringType is null and :gatheringType is null)
                    or r.gathering.gatheringType = :gatheringType
                  )
            """)
    long countByGatheringReviewGroupAndDeletedAtIsNullAndCreatedAtAfter(
            String title, GatheringType gatheringType, LocalDateTime createdAt);

    @Query("""
            select count(r) from Review r
            where r.deletedAt is null
              and r.likeCount > :likeCount
              and r.gathering.title = :title
              and (
                    (r.gathering.gatheringType is null and :gatheringType is null)
                    or r.gathering.gatheringType = :gatheringType
                  )
            """)
    long countByGatheringReviewGroupAndDeletedAtIsNullAndLikeCountGreaterThan(
            String title, GatheringType gatheringType, Integer likeCount);

    @Query("""
            select count(r) from Review r
            where r.deletedAt is null
              and r.likeCount = :likeCount
              and r.createdAt > :createdAt
              and r.gathering.title = :title
              and (
                    (r.gathering.gatheringType is null and :gatheringType is null)
                    or r.gathering.gatheringType = :gatheringType
                  )
            """)
    long countByGatheringReviewGroupAndDeletedAtIsNullAndLikeCountAndCreatedAtAfter(
            String title, GatheringType gatheringType, Integer likeCount, LocalDateTime createdAt);
}
