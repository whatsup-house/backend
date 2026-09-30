package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningPrefillResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 우연한 식탁 참가자 API(/api/dining)의 신청 조회·프리필·사전 취소. 신청 생성은 ApplicationService.apply가 맡는다. (KAN-342)
 * MatchingService가 GatheringService → ApplicationService에 의존하므로 순환을 피하려 ApplicationService와 분리했다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiningApplicationService {

    // 회차 시작 이 일수 전까지 사전 취소할 수 있다.
    private static final int CANCEL_DEADLINE_DAYS = 2;
    private static final Set<ApplicationStatus> CANCELLABLE =
            Set.of(ApplicationStatus.PENDING, ApplicationStatus.PAYMENT_PENDING, ApplicationStatus.CONFIRMED);

    private final ApplicationRepository applicationRepository;
    private final ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final TicketService ticketService;
    private final MatchingService matchingService;
    private final DiningAttendanceService diningAttendanceService;
    private final FormService formService;
    private final ApplicationEventPublisher eventPublisher;

    /** 내 우연한 식탁 신청 + 희망/배정 회차 + 매칭·이용권 상태 + 테이블 요약. */
    public DiningApplicationListResponse listMyApplications(UUID userId) {
        List<Application> applications = applicationRepository
                .findByUser_IdAndGathering_GatheringTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
                        userId, GatheringType.RANDOM_TABLE);
        if (applications.isEmpty()) {
            return DiningApplicationListResponse.builder().applications(List.of()).build();
        }
        List<UUID> ids = applications.stream().map(Application::getId).toList();
        Map<UUID, List<GatheringSession>> candidates = findCandidateSessions(ids);
        Map<UUID, TicketDeductionStatus> ticketStatuses = ticketService.findDeductionStatuses(ids);
        Map<UUID, DiningTable> tables = matchingService.findTablesByApplicationIds(ids);
        return DiningApplicationListResponse.builder()
                .applications(applications.stream()
                        .map(a -> DiningApplicationListResponse.Item.of(a,
                                candidates.getOrDefault(a.getId(), List.of()),
                                ticketStatuses.get(a.getId()),
                                tables.get(a.getId())))
                        .toList())
                .build();
    }

    /**
     * 신청 폼 프리필(ACC-06): 이 모임 폼의 표준 질문마다 내 가장 최근 답변을 준다.
     * 표준 질문은 reserved_key로 대응시키므로 다른 모임에서 답한 값도 쓴다. 답한 적 없는 질문은 빠진다.
     */
    public DiningPrefillResponse getPrefill(UUID userId, UUID gatheringId) {
        Map<ReservedQuestionKey, String> questionKeys = formService.findReservedQuestionKeys(gatheringId);
        Map<ReservedQuestionKey, Object> latest = new EnumMap<>(ReservedQuestionKey.class);
        // 최신순이라 표준 질문별 첫 값이 가장 최근 답변이다.
        for (ApplicationAnswer answer : applicationAnswerRepository.findReservedAnswersByUserId(userId)) {
            Object value = answer.getValue() != null ? answer.getValue().get("value") : null;
            if (value != null) {
                latest.putIfAbsent(answer.getQuestion().getReservedKey(), value);
            }
        }
        return new DiningPrefillResponse(questionKeys.entrySet().stream()
                .filter(entry -> latest.containsKey(entry.getKey()))
                .map(entry -> new DiningPrefillResponse.Answer(
                        entry.getKey(), entry.getValue(), latest.get(entry.getKey())))
                .toList());
    }

    /**
     * 사전 취소: 회차 시작 2일 전까지. 차감한 이용권은 복원(RESTORED)하고 신청은 CANCELLED.
     * 기준 회차는 배정 회차, 배정 전(매칭 대기)이면 아직 시작하지 않은 희망 회차 중 가장 이른 회차.
     */
    @Transactional
    public void cancelApplication(UUID applicationId, UUID userId) {
        // 취소하면 soft delete되므로 삭제된 신청까지 찾아야 "이미 취소됨(409)"을 구분할 수 있다.
        Application application = applicationRepository.findIncludingDeletedById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }
        if (application.getStatus() == ApplicationStatus.CANCELLED) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_CANCELLED);
        }
        if (application.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        if (application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE
                || !CANCELLABLE.contains(application.getStatus())) {
            throw new CustomException(ErrorCode.CANNOT_CANCEL);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(findCancelBaseStartAt(application, now).minusDays(CANCEL_DEADLINE_DAYS))) {
            throw new CustomException(ErrorCode.CANCEL_WINDOW_CLOSED);
        }

        application.cancel();
        // 차감 기록(USE)이 있으면 RESTORED로 바꾸고 잔여를 1회 복구한다. 결제 대기(차감 없음)나
        // 회차 취소로 이미 복구된 신청은 refundOneTicket이 무시한다(멱등).
        ticketService.refundOneTicket(application);
        // 확정 테이블 멤버였다면 참석을 CANCELED_EARLY로(참석 행이 있을 때만). 재조정으로 좌석이 빠지기 전에 기록한다. (KAN-349)
        diningAttendanceService.cancelAttendance(applicationId);
        // TODO(KAN-347): 확정 테이블 멤버였다면 DiningTableService.rebalance(table)로 충원·재배치한다. (설계 4.7)
        eventPublisher.publishEvent(new ApplicationCancelledEvent(application));
    }

    private LocalDateTime findCancelBaseStartAt(Application application, LocalDateTime now) {
        if (application.getSession() != null) {
            return application.getSession().getStartAt();
        }
        // 이미 지난 희망 회차는 더 배정될 수 없으므로 기준에서 뺀다. 남은 회차가 없으면 취소 기한도 지난 것이다.
        return findCandidateSessions(List.of(application.getId()))
                .getOrDefault(application.getId(), List.of()).stream()
                .map(GatheringSession::getStartAt)
                .filter(startAt -> startAt.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new CustomException(ErrorCode.CANCEL_WINDOW_CLOSED));
    }

    // 신청 ID → 희망 회차(우선순위 순)
    private Map<UUID, List<GatheringSession>> findCandidateSessions(Collection<UUID> applicationIds) {
        return applicationCandidateSessionRepository.findByApplicationIdsWithSession(applicationIds).stream()
                .collect(Collectors.groupingBy(candidate -> candidate.getApplication().getId(),
                        Collectors.mapping(ApplicationCandidateSession::getSession, Collectors.toList())));
    }
}
