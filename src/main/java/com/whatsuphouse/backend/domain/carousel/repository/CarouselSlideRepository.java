package com.whatsuphouse.backend.domain.carousel.repository;

import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarouselSlideRepository extends JpaRepository<CarouselSlide, UUID> {

    @EntityGraph(attributePaths = "gathering")
    List<CarouselSlide> findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();

    @EntityGraph(attributePaths = "gathering")
    List<CarouselSlide> findByDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();

    @Query("SELECT MAX(c.sortOrder) FROM CarouselSlide c WHERE c.deletedAt IS NULL")
    Optional<Integer> findMaxSortOrder();

    Optional<CarouselSlide> findByIdAndDeletedAtIsNull(UUID id);

    List<CarouselSlide> findAllByIdInAndDeletedAtIsNull(List<UUID> ids);
}
