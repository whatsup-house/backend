package com.whatsuphouse.backend.domain.carousel.admin.service;

import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideActiveRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideCreateRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideOrderRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.request.CarouselSlideUpdateRequest;
import com.whatsuphouse.backend.domain.carousel.admin.dto.response.AdminCarouselSlideResponse;
import com.whatsuphouse.backend.domain.carousel.entity.CarouselSlide;
import com.whatsuphouse.backend.domain.carousel.enums.SlideType;
import com.whatsuphouse.backend.domain.carousel.repository.CarouselSlideRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminCarouselService {

    private final CarouselSlideRepository carouselSlideRepository;
    private final GatheringRepository gatheringRepository;
    private final StorageService storageService;

    public List<AdminCarouselSlideResponse> listSlides() {
        return carouselSlideRepository.findByDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc()
                .stream()
                .map(AdminCarouselSlideResponse::from)
                .toList();
    }

    @Transactional
    public AdminCarouselSlideResponse createSlide(CarouselSlideCreateRequest request) {
        validateTypeConstraints(request.getType(), request.getGatheringId(), request.getContent());

        Gathering gathering = null;
        if (request.getType() == SlideType.GATHERING) {
            gathering = gatheringRepository.findByIdAndDeletedAtIsNull(request.getGatheringId())
                    .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        }

        int sortOrder = request.getSortOrder() != null
                ? request.getSortOrder()
                : carouselSlideRepository.findMaxSortOrder().orElse(-1) + 1;

        String content = request.getType() == SlideType.GATHERING ? null : request.getContent();
        Gathering finalGathering = request.getType() != SlideType.GATHERING ? null : gathering;

        // Storage move는 @Transactional 내부에서 호출됨. DB save 실패 시 파일은 롤백 불가.
        // 소규모 어드민 API 특성상 현 구조를 유지하며 trade-off를 허용함 (Gathering과 동일 패턴).
        String imageUrl = storageService.move(request.getTempPath(), "carousel");

        CarouselSlide slide = CarouselSlide.builder()
                .type(request.getType())
                .title(request.getTitle())
                .content(content)
                .imageUrl(imageUrl)
                .gathering(finalGathering)
                .sortOrder(sortOrder)
                .isActive(false)
                .build();

        return AdminCarouselSlideResponse.from(carouselSlideRepository.save(slide));
    }

    @Transactional
    public AdminCarouselSlideResponse updateSlide(UUID slideId, CarouselSlideUpdateRequest request) {
        CarouselSlide slide = carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)
                .orElseThrow(() -> new CustomException(ErrorCode.SLIDE_NOT_FOUND));

        validateTypeConstraints(request.getType(), request.getGatheringId(), request.getContent());

        Gathering gathering = null;
        if (request.getType() == SlideType.GATHERING) {
            gathering = gatheringRepository.findByIdAndDeletedAtIsNull(request.getGatheringId())
                    .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        }

        String content = request.getType() == SlideType.GATHERING ? null : request.getContent();
        Gathering finalGathering = request.getType() != SlideType.GATHERING ? null : gathering;

        int sortOrder = request.getSortOrder() != null
                ? request.getSortOrder()
                : slide.getSortOrder();

        // tempPath가 있으면 새 이미지로 교체하고, 없으면 기존 이미지를 유지한다. (KAN-182)
        // Storage move는 @Transactional 내부에서 호출됨. DB save 실패 시 파일은 롤백 불가.
        String imageUrl = (request.getTempPath() != null && !request.getTempPath().isBlank())
                ? storageService.move(request.getTempPath(), "carousel")
                : slide.getImageUrl();
        slide.update(request.getType(), request.getTitle(), content, imageUrl, finalGathering, sortOrder);

        return AdminCarouselSlideResponse.from(slide);
    }

    @Transactional
    public void deleteSlide(UUID slideId) {
        CarouselSlide slide = carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)
                .orElseThrow(() -> new CustomException(ErrorCode.SLIDE_NOT_FOUND));
        slide.delete();
    }

    @Transactional
    public void toggleActive(UUID slideId, CarouselSlideActiveRequest request) {
        CarouselSlide slide = carouselSlideRepository.findByIdAndDeletedAtIsNull(slideId)
                .orElseThrow(() -> new CustomException(ErrorCode.SLIDE_NOT_FOUND));

        boolean active = Boolean.TRUE.equals(request.getIsActive());
        if (active) {
            slide.activate();
        } else {
            slide.deactivate();
        }
    }

    @Transactional
    public void reorderSlides(CarouselSlideOrderRequest request) {
        List<UUID> slideIds = request.getSlideIds();
        Map<UUID, CarouselSlide> slideMap = carouselSlideRepository.findAllByIdInAndDeletedAtIsNull(slideIds)
                .stream()
                .collect(Collectors.toMap(CarouselSlide::getId, s -> s));

        for (int i = 0; i < slideIds.size(); i++) {
            CarouselSlide slide = Optional.ofNullable(slideMap.get(slideIds.get(i)))
                    .orElseThrow(() -> new CustomException(ErrorCode.SLIDE_NOT_FOUND));
            slide.updateSortOrder(i);
        }
    }

    private void validateTypeConstraints(SlideType type, UUID gatheringId, String content) {
        if (type == SlideType.GATHERING && gatheringId == null) {
            throw new CustomException(ErrorCode.GATHERING_ID_REQUIRED);
        }
        if (type == SlideType.STORY && (content == null || content.isBlank())) {
            throw new CustomException(ErrorCode.SLIDE_CONTENT_REQUIRED);
        }
    }
}
