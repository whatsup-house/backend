package com.whatsuphouse.backend.domain.carousel.service;

import com.whatsuphouse.backend.domain.carousel.client.service.CarouselService;
import com.whatsuphouse.backend.domain.carousel.common.dto.response.CarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.domain.carousel.repository.CarouselSlideRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CarouselServiceTest {

    @Mock
    private CarouselSlideRepository carouselSlideRepository;

    @Mock
    private GatheringService gatheringService;

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
    @DisplayName("GATHERING 슬라이드는 종류 ID가 아니라 대표 회차 ID·날짜로 연결된다 (KAN-337)")
    void listActiveSlides_gatheringSlide_linksRepresentativeSession() {
        // given: 마이그레이션된 종류 — 종류 ID는 가장 오래된 회차 ID와 같으므로 링크는 대표 회차여야 한다
        Gathering gathering = Gathering.builder().title("퇴근 게더링").build();
        UUID gatheringId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(gathering, "id", gatheringId);
        GatheringSession representative = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(3)).maxAttendees(10).build();
        UUID representativeId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(representative, "id", representativeId);
        CarouselSlide gatheringSlide = CarouselSlide.builder()
                .type(SlideType.GATHERING)
                .title("퇴근 게더링")
                .imageUrl("https://cdn.example.com/slide.jpg")
                .gathering(gathering)
                .sortOrder(1)
                .isActive(true)
                .build();
        given(carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc())
                .willReturn(List.of(gatheringSlide));
        given(gatheringService.findRepresentativeSessions(List.of(gatheringId)))
                .willReturn(Map.of(gatheringId, representative));

        // when
        List<CarouselSlideResponse> result = carouselService.listActiveSlides();

        // then
        assertThat(result.get(0).getGatheringId()).isEqualTo(representativeId);
        assertThat(result.get(0).getDateLabel()).isEqualTo(LocalDate.now().plusDays(3).toString());
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
