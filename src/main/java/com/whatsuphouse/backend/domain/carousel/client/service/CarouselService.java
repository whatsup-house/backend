package com.whatsuphouse.backend.domain.carousel.client.service;

import com.whatsuphouse.backend.domain.carousel.common.dto.response.CarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.repository.CarouselSlideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CarouselService {

    private final CarouselSlideRepository carouselSlideRepository;

    public List<CarouselSlideResponse> listActiveSlides() {
        return carouselSlideRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc()
                .stream()
                .map(CarouselSlideResponse::from)
                .toList();
    }
}
