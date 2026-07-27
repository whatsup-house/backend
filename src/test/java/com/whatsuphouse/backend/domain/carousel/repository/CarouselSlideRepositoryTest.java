package com.whatsuphouse.backend.domain.carousel.repository;

import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class CarouselSlideRepositoryTest {

    @Autowired
    private CarouselSlideRepository carouselSlideRepository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void setUp() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("비활성 슬라이드는 활성 슬라이드 목록 조회에서 제외")
    void findByIsActiveTrueAndDeletedAtIsNull_excludesInactive() {
        saveSlide("활성 슬라이드", 0, true);
        saveSlide("비활성 슬라이드", 1, false);
        em.flush();
        em.clear();

        List<CarouselSlide> result =
                carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("활성 슬라이드");
    }

    @Test
    @DisplayName("삭제된 슬라이드는 활성 슬라이드 목록 조회에서 제외")
    void findByIsActiveTrueAndDeletedAtIsNull_excludesDeleted() {
        saveSlide("활성 슬라이드", 0, true);
        CarouselSlide deleted = saveSlide("삭제된 활성 슬라이드", 1, true);
        deleted.delete();
        em.flush();
        em.clear();

        List<CarouselSlide> result =
                carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("활성 슬라이드");
    }

    @Test
    @DisplayName("활성 슬라이드 목록은 sortOrder ASC 정렬")
    void findByIsActiveTrueAndDeletedAtIsNull_sortsBySortOrderAsc() {
        saveSlide("순서2 슬라이드", 2, true);
        saveSlide("순서0 슬라이드", 0, true);
        saveSlide("순서1 슬라이드", 1, true);
        em.flush();
        em.clear();

        List<CarouselSlide> result =
                carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSortOrder()).isZero();
        assertThat(result.get(1).getSortOrder()).isEqualTo(1);
        assertThat(result.get(2).getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제된 슬라이드는 전체 목록 조회에서 제외되고 비활성 슬라이드는 포함")
    void findByDeletedAtIsNull_excludesDeletedButIncludesInactive() {
        saveSlide("활성 슬라이드", 0, true);
        saveSlide("비활성 슬라이드", 1, false);
        CarouselSlide deleted = saveSlide("삭제된 슬라이드", 2, true);
        deleted.delete();
        em.flush();
        em.clear();

        List<CarouselSlide> result =
                carouselSlideRepository.findByDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();

        assertThat(result)
                .hasSize(2)
                .extracting(CarouselSlide::getTitle)
                .contains("활성 슬라이드", "비활성 슬라이드");
    }

    @Test
    @DisplayName("슬라이드 있을 때 findMaxSortOrder가 MAX 값 반환")
    void findMaxSortOrder_withSlides_returnsMax() {
        saveSlide("슬라이드A", 3, true);
        saveSlide("슬라이드B", 1, true);
        saveSlide("슬라이드C", 5, false);
        em.flush();
        em.clear();

        Optional<Integer> result = carouselSlideRepository.findMaxSortOrder();

        assertThat(result).hasValue(5);
    }

    @Test
    @DisplayName("슬라이드 없을 때 findMaxSortOrder가 Optional.empty() 반환")
    void findMaxSortOrder_noSlides_returnsEmpty() {
        Optional<Integer> result = carouselSlideRepository.findMaxSortOrder();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 슬라이드 단건 조회 시 Optional.empty() 반환")
    void findByIdAndDeletedAtIsNull_deleted_returnsEmpty() {
        CarouselSlide slide = saveSlide("삭제될 슬라이드", 0, true);
        UUID id = slide.getId();
        slide.delete();
        em.flush();
        em.clear();

        Optional<CarouselSlide> result = carouselSlideRepository.findByIdAndDeletedAtIsNull(id);

        assertThat(result).isEmpty();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private CarouselSlide saveSlide(String title, int sortOrder, boolean isActive) {
        return carouselSlideRepository.save(CarouselSlide.builder()
                .type(SlideType.STORY)
                .title(title)
                .imageUrl("https://cdn.example.com/slide.jpg")
                .sortOrder(sortOrder)
                .isActive(isActive)
                .build());
    }
}
