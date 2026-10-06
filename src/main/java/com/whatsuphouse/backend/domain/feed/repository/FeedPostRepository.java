package com.whatsuphouse.backend.domain.feed.repository;

import com.whatsuphouse.backend.domain.feed.entity.FeedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedPostRepository extends JpaRepository<FeedPost, UUID> {

    @EntityGraph(attributePaths = "gathering")
    Optional<FeedPost> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = "gathering")
    Page<FeedPost> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = "gathering")
    Page<FeedPost> findByIsVisibleAndDeletedAtIsNull(boolean isVisible, Pageable pageable);

    // 공개 피드 keyset: (postedAt, id) 가 커서보다 앞선(더 오래된) 노출 게시물. (KAN-380)
    @EntityGraph(attributePaths = "gathering")
    @Query("""
            select p from FeedPost p
            where p.isVisible = true
              and p.deletedAt is null
              and (p.postedAt < :at or (p.postedAt = :at and p.id < :id))
            order by p.postedAt desc, p.id desc
            """)
    List<FeedPost> findVisibleBefore(@Param("at") LocalDateTime at, @Param("id") UUID id, Pageable pageable);
}
