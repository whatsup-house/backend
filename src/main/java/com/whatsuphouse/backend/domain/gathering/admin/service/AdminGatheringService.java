package com.whatsuphouse.backend.domain.gathering.admin.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository.ApplicationCountProjection;
import com.whatsuphouse.backend.domain.notification.event.GatheringCancelledEvent;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationOrderRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.response.AdminGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminGatheringService {

    private final GatheringRepository gatheringRepository;
    private final LocationRepository locationRepository;
    private final ApplicationRepository applicationRepository;
    private final StorageService storageService;
    private final FormProvisionService formProvisionService;
    private final ApplicationEventPublisher eventPublisher;

    public List<AdminGatheringResponse> listGatherings(
            GatheringStatus status, LocalDate eventDate, LocalDate from, LocalDate to) {

        List<Gathering> gatherings = resolveGatherings(status, eventDate, from, to);
        if (gatherings.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> gatheringIds = gatherings.stream().map(Gathering::getId).toList();

        Map<UUID, Map<ApplicationStatus, Long>> countMap = applicationRepository
                .countByGatheringIdsGroupByStatus(gatheringIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ApplicationCountProjection::getGatheringId,
                        Collectors.groupingBy(
                                ApplicationCountProjection::getStatus,
                                Collectors.summingLong(ApplicationCountProjection::getCount))));

        return gatherings.stream()
                .map(g -> AdminGatheringResponse.from(g, countMap.getOrDefault(g.getId(), Map.of())))
                .toList();
    }

    private List<Gathering> resolveGatherings(
            GatheringStatus status, LocalDate eventDate, LocalDate from, LocalDate to) {

        if (eventDate != null) {
            return status != null
                    ? gatheringRepository.findByEventDateAndStatusAndDeletedAtIsNull(eventDate, status)
                    : gatheringRepository.findByEventDateAndDeletedAtIsNull(eventDate);
        }
        if (from != null && to != null) {
            return status != null
                    ? gatheringRepository.findByEventDateBetweenAndStatusAndDeletedAtIsNull(from, to, status)
                    : gatheringRepository.findByEventDateBetweenAndDeletedAtIsNull(from, to);
        }
        return status != null
                ? gatheringRepository.findByStatusAndDeletedAtIsNull(status)
                : gatheringRepository.findByDeletedAtIsNull();
    }

    @Transactional
    public GatheringDetailResponse createGathering(GatheringCreateRequest request) {
        Location location = locationRepository.findByIdAndDeletedAtIsNull(request.getLocationId())
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        // Storage move는 @Transactional 내부에서 호출됨. DB save 실패 시 파일은 롤백 불가.
        // 소규모 어드민 API 특성상 현 구조를 유지하며 trade-off를 허용함 (Carousel과 동일 패턴).
        String thumbnailUrl = StringUtils.hasText(request.getThumbnailUrl())
                ? storageService.move(request.getThumbnailUrl(), "gathering")
                : null;
        Gathering gathering = Gathering.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .location(location)
                .eventDate(request.getEventDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .price(request.getPrice())
                .maxAttendees(request.getMaxAttendees())
                .thumbnailUrl(thumbnailUrl)
                .build();
        Gathering saved = gatheringRepository.save(gathering);
        // 신청폼 + 시스템 예약 질문(이름/연락처) 자동 생성
        formProvisionService.createDefaultForm(saved);
        return GatheringDetailResponse.from(saved);
    }

    @Transactional
    public GatheringDetailResponse updateGathering(UUID id, GatheringUpdateRequest request) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        Location location = locationRepository.findByIdAndDeletedAtIsNull(request.getLocationId())
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        // Storage move는 @Transactional 내부에서 호출됨. DB save 실패 시 파일은 롤백 불가.
        // 소규모 어드민 API 특성상 현 구조를 유지하며 trade-off를 허용함 (Carousel과 동일 패턴).
        String thumbnailUrl = StringUtils.hasText(request.getThumbnailUrl())
                ? storageService.move(request.getThumbnailUrl(), "gathering")
                : gathering.getThumbnailUrl();
        gathering.update(request.getTitle(), request.getDescription(), location,
                request.getEventDate(), request.getStartTime(), request.getEndTime(),
                request.getPrice(), request.getMaxAttendees(), thumbnailUrl);
        return GatheringDetailResponse.from(gathering);
    }

    @Transactional
    public void changeStatus(UUID id, GatheringStatusRequest request) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        gathering.changeStatus(request.getStatus());

        // 모임이 취소 상태로 전환될 때 PENDING/CONFIRMED 신청자 전원에게 알림 발송 (FR-NTF-06)
        // 이미 CANCELLED/ATTENDED인 신청자는 알림 대상에서 제외합니다.
        if (request.getStatus() == GatheringStatus.CANCELLED) {
            List<Application> targets = applicationRepository.findByGatheringIdAndStatusInWithUser(
                    id, List.of(ApplicationStatus.PENDING, ApplicationStatus.CONFIRMED));
            if (!targets.isEmpty()) {
                eventPublisher.publishEvent(new GatheringCancelledEvent(gathering, targets));
            }
        }
    }

    @Transactional
    public void toggleCuration(UUID id, GatheringCurationRequest request) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        gathering.updateCuration(request.getIsCurated());
    }

    @Transactional
    public void reorderCurated(GatheringCurationOrderRequest request) {
        List<UUID> ids = request.getGatheringIds();
        Map<UUID, Gathering> gatheringMap = gatheringRepository.findByIdInAndDeletedAtIsNull(ids)
                .stream()
                .collect(Collectors.toMap(Gathering::getId, g -> g));

        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            Gathering gathering = Optional.ofNullable(gatheringMap.get(id))
                    .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
            gathering.updateCuratedRank(i);
        }
    }
}
