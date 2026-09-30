package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.matching.dto.request.ResolutionChooseRequest;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchResolutionResponse;
import com.whatsuphouse.backend.domain.matching.entity.MatchResolution;
import com.whatsuphouse.backend.domain.matching.enums.ResolutionStatus;
import com.whatsuphouse.backend.domain.matching.repository.MatchResolutionRepository;
import com.whatsuphouse.backend.domain.notification.enums.NotificationType;
import com.whatsuphouse.backend.domain.notification.event.DiningResolutionEvent;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매칭 실패 해결 선택(MatchResolution): 마지막 희망 회차에서도 못 앉은 신청에 대체 회차 이동·이용권 보관·환불을 제안하고
 * 참가자 선택·기한 만료를 처리한다. (설계 4.5-4, 4.8, KAN-347)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchResolutionService {

    private final MatchResolutionRepository matchResolutionRepository;
    private final GatheringService gatheringService;
    private final AdminApplicationService adminApplicationService;
    private final TicketService ticketService;
    private final ExceptionCaseService exceptionCaseService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${dining.resolution-respond-hours:48}")
    private long respondHours;

    /**
     * 해결 선택을 제안한다. 제안 회차 = 같은 종류에서 신청자가 고르지 않은, 모집 중(OPEN)·신청 마감 전·매칭 실행 시각이 미래인 회차.
     * 없으면 match_status를 NO_MATCH로 두고 KEEP_TICKET/REFUND만 남긴다.
     */
    @Transactional
    public void offerResolution(Application application, Collection<UUID> wishedSessionIds) {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> offered = gatheringService.listSessionsByGathering(application.getGathering().getId()).stream()
                .filter(session -> !wishedSessionIds.contains(session.getId()) && isOfferable(session, now))
                .map(GatheringSession::getId)
                .toList();
        if (offered.isEmpty()) {
            application.changeMatchStatus(MatchStatus.NO_MATCH);
        }
        matchResolutionRepository.save(MatchResolution.offer(application, offered, now.plusHours(respondHours)));
        eventPublisher.publishEvent(new DiningResolutionEvent(application, NotificationType.DINING_ALTERNATIVE_OFFERED));
    }

    public MatchResolutionResponse getResolution(UUID resolutionId, UUID userId) {
        MatchResolution resolution = matchResolutionRepository.findById(resolutionId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOLUTION_NOT_FOUND));
        checkOwner(resolution, userId);
        return MatchResolutionResponse.of(resolution, findOfferableSessions(resolution));
    }

    /** 참가자 선택. 응답 대기(OFFERED)인 제안만 처리하며, 행 잠금으로 중복 요청·만료 처리와 직렬화한다. */
    @Transactional
    public MatchResolutionResponse chooseResolution(UUID resolutionId, UUID userId, ResolutionChooseRequest request) {
        MatchResolution resolution = matchResolutionRepository.findByIdForUpdate(resolutionId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOLUTION_NOT_FOUND));
        checkOwner(resolution, userId);
        Application application = resolution.getApplication();
        // 신청을 따로 취소(사전 취소)했으면 이미 이용권이 복원돼 더 처리할 것이 없다.
        if (!resolution.isOffered() || application.getStatus() == ApplicationStatus.CANCELLED) {
            throw new CustomException(ErrorCode.RESOLUTION_ALREADY_HANDLED);
        }
        UUID chosenSessionId = null;
        switch (request.getChoice()) {
            case TRANSFER -> chosenSessionId = transfer(resolution, request.getSessionId());
            case KEEP_TICKET -> keepTicket(application);
            case REFUND -> refund(resolution, application);
        }
        resolution.resolve(request.getChoice(), chosenSessionId);
        return MatchResolutionResponse.of(resolution, findOfferableSessions(resolution));
    }

    public List<UUID> listExpiredResolutionIds() {
        return matchResolutionRepository.findIdsByStatusAndRespondByBefore(ResolutionStatus.OFFERED, LocalDateTime.now());
    }

    /** 응답 기한이 지난 제안을 EXPIRED로 바꾸고 이용권 보관(KEEP_TICKET)과 같이 처리한다. 스케줄러가 건별 트랜잭션으로 부른다. */
    @Transactional
    public void expireResolution(UUID resolutionId) {
        MatchResolution resolution = matchResolutionRepository.findByIdForUpdate(resolutionId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOLUTION_NOT_FOUND));
        // 잠그기 전에 참가자가 먼저 골랐을 수 있다.
        if (!resolution.isOffered() || resolution.getRespondBy().isAfter(LocalDateTime.now())) {
            return;
        }
        resolution.expire();
        if (resolution.getApplication().getStatus() != ApplicationStatus.CANCELLED) {
            keepTicket(resolution.getApplication());
        }
    }

    /** 신청 ID → 응답 대기(OFFERED) 중인 해결 선택 ID. 없으면 결과에 없다. (내 신청 조회용) */
    public Map<UUID, UUID> findOfferedResolutionIds(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return matchResolutionRepository.findByApplication_IdInAndStatus(applicationIds, ResolutionStatus.OFFERED).stream()
                .collect(Collectors.toMap(r -> r.getApplication().getId(), MatchResolution::getId, (first, second) -> first));
    }

    // ── 선택 처리 ────────────────────────────────────────────────────────────

    // 희망 회차를 고른 회차 1개로 바꾸고 매칭 대기로 돌린다. 결제(차감)는 유지.
    private UUID transfer(MatchResolution resolution, UUID sessionId) {
        if (sessionId == null) {
            throw new CustomException(ErrorCode.RESOLUTION_SESSION_REQUIRED);
        }
        GatheringSession session = findOfferableSessions(resolution).stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.RESOLUTION_SESSION_NOT_OFFERED));
        adminApplicationService.transferApplication(resolution.getApplication(), session);
        eventPublisher.publishEvent(new DiningResolutionEvent(resolution.getApplication(), NotificationType.DINING_TRANSFERRED));
        return sessionId;
    }

    // 차감한 이용권을 되돌리고(RESTORED) 신청을 취소한다.
    private void keepTicket(Application application) {
        ticketService.refundOneTicket(application);
        application.cancel();
    }

    // 모의 환불. 구매 건(차감 기록)을 특정할 수 없으면 이용권 보관과 같이 처리한다.
    private void refund(MatchResolution resolution, Application application) {
        Optional<TicketDeductionStatus> result = ticketService.refundPurchase(application);
        if (result.isEmpty()) {
            log.info("[MatchResolution] 환불할 이용권 차감 기록이 없어 이용권 보관으로 처리: resolutionId={}, applicationId={}",
                    resolution.getId(), application.getId());
            keepTicket(application);
            return;
        }
        application.cancel();
        eventPublisher.publishEvent(new DiningResolutionEvent(application, NotificationType.DINING_REFUND_REQUESTED));
        if (result.get() == TicketDeductionStatus.REFUNDED) {
            eventPublisher.publishEvent(new DiningResolutionEvent(application, NotificationType.DINING_REFUND_COMPLETED));
            return;
        }
        exceptionCaseService.open(ExceptionCaseType.REFUND,
                application.getSession() != null ? application.getSession().getId() : null, null, application.getId(),
                "매칭 실패 환불 자동 처리 실패(이용권 차감 기록 " + result.get() + "). 수동 환불 필요");
    }

    // ── 조회 헬퍼 ────────────────────────────────────────────────────────────

    // 제안 회차 중 지금도 옮길 수 있는 회차, 제안 순서대로.
    private List<GatheringSession> findOfferableSessions(MatchResolution resolution) {
        if (resolution.getOfferedSessionIds().isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, GatheringSession> sessions = gatheringService
                .listSessionsByGathering(resolution.getApplication().getGathering().getId()).stream()
                .filter(session -> isOfferable(session, now))
                .collect(Collectors.toMap(GatheringSession::getId, Function.identity()));
        return resolution.getOfferedSessionIds().stream().map(sessions::get).filter(Objects::nonNull).toList();
    }

    private static boolean isOfferable(GatheringSession session, LocalDateTime now) {
        return session.getEffectiveSessionStatus() == GatheringSessionStatus.OPEN
                && (session.getApplyDeadlineAt() == null || session.getApplyDeadlineAt().isAfter(now))
                && session.getMatchRunAt() != null && session.getMatchRunAt().isAfter(now);
    }

    private static void checkOwner(MatchResolution resolution, UUID userId) {
        Application application = resolution.getApplication();
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }
    }
}
