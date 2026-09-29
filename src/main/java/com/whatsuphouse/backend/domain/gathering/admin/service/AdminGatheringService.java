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
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.form.admin.service.FormProvisionService;
import com.whatsuphouse.backend.domain.location.entity.Location;
import com.whatsuphouse.backend.domain.location.repository.LocationRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.event.ContentTranslationRequestedEvent;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminGatheringService {

    // StorageService.upload()가 반환하는 임시 경로 접두사. 이 값으로 시작할 때만 move 대상이다.
    private static final String TEMP_PATH_PREFIX = "temp/";

    private final GatheringRepository gatheringRepository;
    private final LocationRepository locationRepository;
    private final ApplicationRepository applicationRepository;
    private final StorageService storageService;
    private final FormProvisionService formProvisionService;
    private final TicketService ticketService;
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
                    ? gatheringRepository.findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                            eventDate, status)
                    : gatheringRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate);
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

    // 게더링 날짜 유효성 검증. 과거 날짜를 차단한다. (KAN-293)
    // 종료 시간이 다음날 새벽일 수 있으므로 시작/종료 시간의 선후 관계는 검증하지 않는다.
    private void validateSchedule(LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        if (eventDate != null && eventDate.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_GATHERING_DATE);
        }
    }

    // 관리자 수정 패널 prefill용 상세 조회. 목록 응답에 없는 소개/장소/시간/썸네일까지 포함한다. (KAN-220)
    public GatheringDetailResponse getGathering(UUID id) {
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        return GatheringDetailResponse.from(gathering);
    }

    @Transactional
    public GatheringDetailResponse createGathering(GatheringCreateRequest request) {
        validateSchedule(request.getEventDate(), request.getStartTime(), request.getEndTime());
        Location location = locationRepository.findByIdAndDeletedAtIsNull(request.getLocationId())
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        String thumbnailUrl = resolveThumbnailUrl(request.getThumbnailUrl(), null);
        Gathering gathering = Gathering.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .howToRun(request.getHowToRun())
                .tags(request.getTags())
                .location(location)
                .eventDate(request.getEventDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .price(request.getPrice())
                .maxAttendees(request.getMaxAttendees())
                .thumbnailUrl(thumbnailUrl)
                .gatheringType(request.getGatheringType())
                .build();
        Gathering saved = gatheringRepository.save(gathering);
        // 신청폼 + 시스템 예약 질문(이름/연락처) 자동 생성
        formProvisionService.createDefaultForm(saved);
        // 커밋 후 ko 원문(title/description)을 en/ja로 자동 번역 (KAN-267)
        publishTranslation(saved);
        return GatheringDetailResponse.from(saved);
    }

    @Transactional
    public GatheringDetailResponse updateGathering(UUID id, GatheringUpdateRequest request) {
        validateSchedule(request.getEventDate(), request.getStartTime(), request.getEndTime());
        Gathering gathering = gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
        Location location = locationRepository.findByIdAndDeletedAtIsNull(request.getLocationId())
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
        String thumbnailUrl = resolveThumbnailUrl(request.getThumbnailUrl(), gathering.getThumbnailUrl());
        gathering.update(request.getTitle(), request.getDescription(), location,
                request.getEventDate(), request.getStartTime(), request.getEndTime(),
                request.getPrice(), request.getMaxAttendees(), thumbnailUrl, request.getHowToRun(),
                request.getTags());
        // 변경된 ko 원문 재번역 (원문 미변경 필드는 해시 비교로 자동 스킵) (KAN-267)
        publishTranslation(gathering);
        return GatheringDetailResponse.from(gathering);
    }

    /**
     * 요청으로 들어온 thumbnailUrl을 저장할 최종 URL로 변환한다.
     *
     * upload()가 돌려주는 임시 경로(temp/...)일 때만 정식 폴더로 move 한다.
     * 수정 폼은 이미 저장된 공개 URL을 그대로 prefill 해서 되돌려보내는데, 그 값을 다시 move 하면
     * sourceKey가 존재하지 않아 IMAGE_UPLOAD_FAILED로 수정 자체가 막힌다. 이미 정식 URL인 값은
     * 이동 없이 그대로 사용한다. (외부 이미지 URL을 직접 입력하는 경우도 동일하게 처리된다.)
     *
     * Storage move는 @Transactional 내부에서 호출됨. DB save 실패 시 파일은 롤백 불가.
     * 소규모 어드민 API 특성상 현 구조를 유지하며 trade-off를 허용함 (Carousel과 동일 패턴).
     */
    private String resolveThumbnailUrl(String requestedThumbnailUrl, String currentThumbnailUrl) {
        if (!StringUtils.hasText(requestedThumbnailUrl)) {
            return currentThumbnailUrl;
        }
        if (requestedThumbnailUrl.startsWith(TEMP_PATH_PREFIX)) {
            return storageService.move(requestedThumbnailUrl, "gathering");
        }
        return requestedThumbnailUrl;
    }

    // 게더링의 번역 대상 ko 필드(title/description)를 자동 번역 이벤트로 발행한다. (KAN-267)
    private void publishTranslation(Gathering gathering) {
        Map<String, String> koFields = new LinkedHashMap<>();
        koFields.put("title", gathering.getTitle());
        if (gathering.getDescription() != null) {
            koFields.put("description", gathering.getDescription());
        }
        eventPublisher.publishEvent(new ContentTranslationRequestedEvent(
                TranslatableType.GATHERING, gathering.getId(), koFields));
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

            // 우연한 식탁 게더링이 취소되면 신청한 회원들에게 차감했던 이용권을 환불한다. (KAN-261)
            // 신청 상태는 그대로 두되, 이후 개별 취소 시 게더링이 CANCELLED면 중복 환불하지 않는다.
            if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
                targets.stream()
                        .forEach(ticketService::refundOneTicket);
            }

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
