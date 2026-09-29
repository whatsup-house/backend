package com.whatsuphouse.backend.domain.gathering.admin.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository.ApplicationSessionCountProjection;
import com.whatsuphouse.backend.domain.notification.event.GatheringCancelledEvent;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationOrderRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.response.AdminGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringSessionResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminGatheringService {

    // StorageService.upload()가 반환하는 임시 경로 접두사. 이 값으로 시작할 때만 move 대상이다.
    private static final String TEMP_PATH_PREFIX = "temp/";

    private final GatheringRepository gatheringRepository;
    private final GatheringSessionRepository gatheringSessionRepository;
    private final GatheringService gatheringService;
    private final LocationRepository locationRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final StorageService storageService;
    private final FormProvisionService formProvisionService;
    private final TicketService ticketService;
    private final ApplicationEventPublisher eventPublisher;

    public List<AdminGatheringResponse> listGatherings(
            GatheringStatus status, LocalDate eventDate, LocalDate from, LocalDate to) {

        // 목록 항목 하나 = 회차 하나(회차 운영용). 종류 ID는 gatheringId로 함께 내려준다.
        List<GatheringSession> sessions = resolveSessions(
                status != null ? GatheringSessionStatus.from(status) : null, eventDate, from, to);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> sessionIds = sessions.stream().map(GatheringSession::getId).toList();

        Map<UUID, Map<ApplicationStatus, Long>> countMap = applicationRepository
                .countBySessionIdsGroupByStatus(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ApplicationSessionCountProjection::getSessionId,
                        Collectors.groupingBy(
                                ApplicationSessionCountProjection::getStatus,
                                Collectors.summingLong(ApplicationSessionCountProjection::getCount))));

        return sessions.stream()
                .map(s -> AdminGatheringResponse.from(s, countMap.getOrDefault(s.getId(), Map.of())))
                .toList();
    }

    private List<GatheringSession> resolveSessions(
            GatheringSessionStatus status, LocalDate eventDate, LocalDate from, LocalDate to) {

        if (eventDate != null) {
            return status != null
                    ? gatheringSessionRepository.findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                            eventDate, status)
                    : gatheringSessionRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(eventDate);
        }
        if (from != null && to != null) {
            return status != null
                    ? gatheringSessionRepository.findByEventDateBetweenAndStatusAndDeletedAtIsNull(from, to, status)
                    : gatheringSessionRepository.findByEventDateBetweenAndDeletedAtIsNull(from, to);
        }
        return status != null
                ? gatheringSessionRepository.findByStatusAndDeletedAtIsNull(status)
                : gatheringSessionRepository.findByDeletedAtIsNull();
    }

    // 회차 날짜 유효성 검증. 과거 날짜를 차단한다. (KAN-293)
    // 종료 시간이 다음날 새벽일 수 있으므로 시작/종료 시간의 선후 관계는 검증하지 않는다.
    private void validateSchedule(LocalDate eventDate) {
        if (eventDate != null && eventDate.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_GATHERING_DATE);
        }
    }

    // 관리자 수정 패널 prefill용 종류 상세 + 전체 회차. 회차 ID로 와도 그 회차의 종류로 응답한다. (KAN-338)
    public GatheringDetailResponse getGathering(UUID id) {
        return gatheringService.getGathering(id);
    }

    // 모임 종류 생성. 회차는 POST /{id}/sessions로 따로 만든다. (KAN-338)
    @Transactional
    public GatheringDetailResponse createGathering(GatheringCreateRequest request) {
        String thumbnailUrl = resolveThumbnailUrl(request.getThumbnailUrl(), null);
        Gathering gathering = gatheringRepository.save(Gathering.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .howToRun(request.getHowToRun())
                .tags(request.getTags())
                .basePrice(request.getBasePrice())
                .thumbnailUrl(thumbnailUrl)
                .gatheringType(request.getGatheringType())
                .build());
        // 신청폼 + 시스템 예약 질문(이름/연락처) 자동 생성
        formProvisionService.createDefaultForm(gathering);
        // 커밋 후 ko 원문(title/description)을 en/ja로 자동 번역 (KAN-267)
        publishTranslation(gathering);
        return GatheringDetailResponse.of(gathering, gathering.getTitle(), gathering.getDescription(), List.of());
    }

    // 모임 종류 수정. 소개·썸네일·기본 가격 등은 이 종류의 모든 회차에 반영된다. 타입은 바꾸지 않는다.
    @Transactional
    public GatheringDetailResponse updateGathering(UUID id, GatheringUpdateRequest request) {
        Gathering gathering = findGathering(id);
        String thumbnailUrl = resolveThumbnailUrl(request.getThumbnailUrl(), gathering.getThumbnailUrl());
        gathering.update(request.getTitle(), request.getDescription(), thumbnailUrl,
                request.getHowToRun(), request.getTags(), request.getBasePrice());
        // 변경된 ko 원문 재번역 (원문 미변경 필드는 해시 비교로 자동 스킵) (KAN-267)
        publishTranslation(gathering);
        return gatheringService.getGathering(id);
    }

    // 모임 종류 삭제(soft delete). 회차도 함께 지운다. 신청이 있는 회차가 하나라도 있으면 막는다.
    @Transactional
    public void deleteGathering(UUID id) {
        Gathering gathering = findGathering(id);
        List<GatheringSession> sessions = gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(id));
        if (applicationService.hasActiveApplications(sessions.stream().map(GatheringSession::getId).toList())) {
            throw new CustomException(ErrorCode.SESSION_HAS_APPLICATIONS);
        }
        sessions.forEach(GatheringSession::delete);
        gathering.delete();
    }

    /**
     * 회차 생성. repeatWeekly가 있으면 기준 회차 날짜부터 until까지 7일 간격으로 만든다(신청 마감도 같은 간격으로 민다). (KAN-338)
     */
    @Transactional
    public List<GatheringSessionResponse> createSessions(UUID gatheringId, GatheringSessionCreateRequest request) {
        Gathering gathering = findGathering(gatheringId);
        validateSchedule(request.getEventDate());
        Location location = findLocation(request.getLocationId());
        LocalDateTime deadline = request.getApplyDeadlineAt();
        List<GatheringSession> sessions = IntStream.range(0, countWeeks(request))
                .mapToObj(week -> GatheringSession.builder()
                        .gathering(gathering)
                        .location(location)
                        .eventDate(request.getEventDate().plusWeeks(week))
                        .startTime(request.getStartTime())
                        .endTime(request.getEndTime())
                        .maxAttendees(request.getMaxAttendees())
                        .priceOverride(request.getPriceOverride())
                        .applyDeadlineAt(deadline != null ? deadline.plusWeeks(week) : null)
                        .build())
                .toList();
        return gatheringSessionRepository.saveAll(sessions).stream()
                .map(session -> GatheringSessionResponse.from(session, 0))
                .toList();
    }

    // 만들 회차 수. 반복이 없으면 1. until은 기준 회차 날짜부터 1년 이내여야 한다(오입력으로 회차가 대량 생성되는 것을 막는다).
    private int countWeeks(GatheringSessionCreateRequest request) {
        if (request.getRepeatWeekly() == null) {
            return 1;
        }
        LocalDate from = request.getEventDate();
        LocalDate until = request.getRepeatWeekly().getUntil();
        if (until.isBefore(from) || until.isAfter(from.plusYears(1))) {
            throw new CustomException(ErrorCode.INVALID_REPEAT_RANGE);
        }
        return (int) ChronoUnit.WEEKS.between(from, until) + 1;
    }

    @Transactional
    public GatheringSessionResponse updateSession(UUID sessionId, GatheringSessionRequest request) {
        validateSchedule(request.getEventDate());
        GatheringSession session = findSession(sessionId);
        Location location = findLocation(request.getLocationId());
        session.update(location, request.getEventDate(), request.getStartTime(), request.getEndTime(),
                request.getPriceOverride(), request.getMaxAttendees(), request.getApplyDeadlineAt());
        return gatheringService.toSessionResponses(List.of(session)).get(0);
    }

    // 회차 삭제(soft delete). 이 회차에 배정됐거나 희망 회차로 고른 활성 신청이 있으면 409.
    @Transactional
    public void deleteSession(UUID sessionId) {
        GatheringSession session = findSession(sessionId);
        if (applicationService.hasActiveApplications(List.of(sessionId))) {
            throw new CustomException(ErrorCode.SESSION_HAS_APPLICATIONS);
        }
        session.delete();
    }

    // 관리자 변경 API는 종류 ID만 받는다(회차 ID로 종류를 지우는 사고 방지).
    private Gathering findGathering(UUID id) {
        return gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
    }

    private GatheringSession findSession(UUID sessionId) {
        return gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
    }

    private Location findLocation(UUID locationId) {
        return locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOCATION_NOT_FOUND));
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
        GatheringSession session = findSession(id);
        Gathering gathering = session.getGathering();
        session.changeStatus(GatheringSessionStatus.from(request.getStatus()));

        // 회차가 취소 상태로 전환될 때 그 회차의 PENDING/CONFIRMED 신청자 전원에게 알림 발송 (FR-NTF-06)
        // 이미 CANCELLED/ATTENDED인 신청자는 알림 대상에서 제외합니다.
        if (request.getStatus() == GatheringStatus.CANCELLED) {
            List<Application> targets = applicationRepository.findBySessionIdAndStatusInWithUser(
                    id, List.of(ApplicationStatus.PENDING, ApplicationStatus.CONFIRMED));

            // 우연한 식탁 회차가 취소되면 신청한 회원들에게 차감했던 이용권을 환불한다. (KAN-261)
            // 신청 상태는 그대로 두되, 이후 개별 취소 시 회차가 CANCELLED면 중복 환불하지 않는다.
            if (gathering.getGatheringType() == GatheringType.RANDOM_TABLE) {
                targets.stream()
                        .forEach(ticketService::refundOneTicket);
            }

            if (!targets.isEmpty()) {
                eventPublisher.publishEvent(new GatheringCancelledEvent(gathering, targets));
            }
        }
    }

    // 큐레이션은 종류 단위. 종류 ID로 오거나 관리자 목록의 회차 ID로 오면 그 회차의 종류에 반영한다.
    @Transactional
    public void toggleCuration(UUID id, GatheringCurationRequest request) {
        gatheringService.findGathering(id).updateCuration(request.getIsCurated());
    }

    // 큐레이션 목록 id는 대표 회차 ID다(기존 API 호환). 종류 ID로 와도 같은 종류로 해석한다. (KAN-337)
    @Transactional
    public void reorderCurated(GatheringCurationOrderRequest request) {
        List<UUID> ids = request.getGatheringIds();
        for (int i = 0; i < ids.size(); i++) {
            gatheringService.findGathering(ids.get(i)).updateCuratedRank(i);
        }
    }
}
