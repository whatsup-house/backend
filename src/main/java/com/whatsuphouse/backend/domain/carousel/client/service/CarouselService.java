package com.whatsuphouse.backend.domain.carousel.client.service;

import com.whatsuphouse.backend.domain.carousel.common.dto.response.CarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.repository.CarouselSlideRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CarouselService {

    private final CarouselSlideRepository carouselSlideRepository;
    private final GatheringService gatheringService;

    // 슬라이드는 모임 종류에 연결된다. 날짜·상태는 종류의 대표 회차 값으로 채운다. (KAN-337)
    public List<CarouselSlideResponse> listActiveSlides() {
        List<CarouselSlide> slides = carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc();
        Map<UUID, GatheringSession> representatives = gatheringService.findRepresentativeSessions(slides.stream()
                .filter(slide -> slide.getGathering() != null)
                .map(slide -> slide.getGathering().getId())
                .distinct()
                .toList());
        return slides.stream()
                .map(slide -> CarouselSlideResponse.from(slide,
                        slide.getGathering() != null ? representatives.get(slide.getGathering().getId()) : null))
                .toList();
    }
}
