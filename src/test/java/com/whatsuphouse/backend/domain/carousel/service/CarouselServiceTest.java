package com.whatsuphouse.backend.domain.carousel.service;

import com.whatsuphouse.backend.domain.carousel.client.service.CarouselService;
import com.whatsuphouse.backend.domain.carousel.common.dto.response.CarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.domain.carousel.repository.CarouselSlideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CarouselServiceTest {

    @Mock
    private CarouselSlideRepository carouselSlideRepository;

    @InjectMocks
    private CarouselService carouselService;

    private CarouselSlide activeSlide;

    @BeforeEach
    void setUp() {
        activeSlide = CarouselSlide.builder()
                .type(SlideType.STORY)
                .title("봄 나들이 모임")
                .content("함께 봄꽃 구경 가요!")
                .imageUrl("https://cdn.example.com/slide.jpg")
                .sortOrder(0)
                .isActive(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(activeSlide, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("활성 슬라이드 목록 반환")
    void listActiveSlides_success() {
        // given
        given(carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc())
                .willReturn(List.of(activeSlide));

        // when
        List<CarouselSlideResponse> result = carouselService.listActiveSlides();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("봄 나들이 모임");
    }

    @Test
    @DisplayName("활성 슬라이드 없을 때 빈 리스트 반환")
    void listActiveSlides_empty() {
        // given
        given(carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc())
                .willReturn(List.of());

        // when
        List<CarouselSlideResponse> result = carouselService.listActiveSlides();

        // then
        assertThat(result).isEmpty();
    }
}
