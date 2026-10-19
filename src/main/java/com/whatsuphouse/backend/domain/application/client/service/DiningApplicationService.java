package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse.DepositOutcome;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningPrefillResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.DiningTableService;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    // 취소한 신청도 이 기간 동안은 내 신청 목록에 남긴다(보증금 상태 배지). (KAN-402)
    private static final int CANCELLED_VISIBLE_DAYS = 30;
    private static final Set<ApplicationStatus> CANCELLABLE =
            Set.of(ApplicationStatus.PENDING, ApplicationStatus.PAYMENT_PENDING, ApplicationStatus.CONFIRMED);

    private final ApplicationRepository applicationRepository;
    private final ApplicationCandidateSessionRepository applicationCandidateSessionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final TicketService ticketService;
    private final MatchingService matchingService;
    private final DiningAttendanceService diningAttendanceService;
    private final DiningTableService diningTableService;
    private final MatchResolutionService matchResolutionService;
    private final FormService formService;
    private final DepositService depositService;
    private final ApplicationEventPublisher eventPublisher;

    /** 내 우연한 식탁 신청(최근 30일 안에 취소한 신청 포함) + 희망/배정 회차 + 매칭·이용권·보증금 상태 + 테이블 요약. */
    public DiningApplicationListResponse listMyApplications(UUID userId) {
        List<Application> applications = applicationRepository.findMyApplicationsIncludingRecentlyCancelled(
                userId, GatheringType.RANDOM_TABLE, ApplicationStatus.CANCELLED,
                LocalDateTime.now().minusDays(CANCELLED_VISIBLE_DAYS));
        if (applications.isEmpty()) {
            return DiningApplicationListResponse.builder().applications(List.of()).build();
        }
        List<UUID> ids = applications.stream().map(Application::getId).toList();
        Map<UUID, List<GatheringSession>> candidates = findCandidateSessions(ids);
        Map<UUID, TicketDeductionStatus> ticketStatuses = ticketService.findDeductionStatuses(ids);
        Map<UUID, DiningTable> tables = matchingService.findTablesByApplicationIds(ids);
        Map<UUID, UUID> resolutionIds = matchResolutionService.findOfferedResolutionIds(ids);
        Map<UUID, Deposit> deposits = depositService.findByApplicationIds(ids);
        return DiningApplicationListResponse.builder()
                .applications(applications.stream()
                        .map(a -> DiningApplicationListResponse.Item.of(a,
                                candidates.getOrDefault(a.getId(), List.of()),
                                ticketStatuses.get(a.getId()),
                                tables.get(a.getId()),
                                resolutionIds.get(a.getId()),
                                deposits.get(a.getId())))
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
     * 취소(설계 4.1, KAN-402). 기준 회차 날짜로 시점을 나눈다. 취소는 돈으로 환불하지 않는다.
     * - 마감 전: 이용권 복원(RESTORED), 참석 CANCELED_EARLY, 보증금 반환 요청.
     * - 마감 후: 복원 없음, 참석 CANCELED_LATE, 보증금 반환 요청.
     * - 당일: 테이블(제안·확정)에 앉은 신청이면 노쇼와 동일 취급(보증금 몰수 + 보증금 대상 지정).
     *   앉지 않은 신청(결제 대기·매칭 전)은 마감 후와 같다. (R18)
     * 회차가 이미 시작했으면 CANCEL_WINDOW_CLOSED. 테이블에 앉아 있었으면 빼고 재조정한다(설계 4.7).
     */
    @Transactional
    public void cancelApplication(UUID applicationId, UUID userId) {
        Application application = findCancellable(applicationId, userId);
        CancelWindow window = evaluateWindow(application);
        DiningTable table = matchingService.findTablesByApplicationIds(List.of(applicationId)).get(applicationId);
        boolean noShow = isNoShowCancel(window, table);

        application.cancel();
        if (window == CancelWindow.BEFORE_DEADLINE) {
            // 차감 기록(USE)이 있으면 RESTORED로 바꾸고 잔여를 1회 복구한다. 결제 대기(차감 없음)나
            // 회차 취소로 이미 복구된 신청은 refundOneTicket이 무시한다(멱등).
            ticketService.refundOneTicket(application);
        }
        // 확정 테이블 멤버였다면 참석을 취소로(참석 행이 있을 때만). 재조정이 멤버 행을 removed_at으로 빼기 전에 기록한다. (KAN-349)
        diningAttendanceService.cancelAttendance(applicationId, window != CancelWindow.BEFORE_DEADLINE);
        if (noShow) {
            depositService.forfeit(applicationId);
            application.getUser().markDepositRequired();
        } else {
            depositService.requestReturn(applicationId);
        }
        // 테이블(제안·확정)에 앉아 있었다면 빼고, 인원이 모자라면 충원·재배치한다. (설계 4.7)
        if (table != null) {
            diningTableService.rebalance(table.getId());
        }
        eventPublisher.publishEvent(new ApplicationCancelledEvent(application));
    }

    /** 지금 취소하면 적용되는 결과(PAY-02). 취소와 같은 검증을 하고 아무것도 바꾸지 않는다. */
    public DiningCancelPreviewResponse previewCancel(UUID applicationId, UUID userId) {
        Application application = findCancellable(applicationId, userId);
        CancelWindow window = evaluateWindow(application);
        boolean noShow = isNoShowCancel(window,
                matchingService.findTablesByApplicationIds(List.of(applicationId)).get(applicationId));
        boolean ticketRestored = window == CancelWindow.BEFORE_DEADLINE && ticketService.hasCountDeduction(applicationId);
        Deposit deposit = depositService.findByApplicationIds(List.of(applicationId)).get(applicationId);
        DepositOutcome depositOutcome;
        if (deposit == null || deposit.getStatus() != DepositStatus.PAID) {
            depositOutcome = DepositOutcome.NONE;    // 돌려주거나 몰수할 입금이 없다
        } else {
            depositOutcome = noShow ? DepositOutcome.FORFEIT : DepositOutcome.RETURN;
        }
        return new DiningCancelPreviewResponse(window, ticketRestored, false, depositOutcome, noShow);
    }

    // 당일 취소는 테이블(제안·확정)에 앉은 신청만 노쇼로 본다. 앉지 않은 신청은 마감 후 취소와 같다. (R18)
    private static boolean isNoShowCancel(CancelWindow window, DiningTable table) {
        return window == CancelWindow.SAME_DAY && table != null;
    }

    private Application findCancellable(UUID applicationId, UUID userId) {
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
        return application;
    }

    /**
     * 기준 회차 날짜로 취소 시점을 정한다. 기준 회차는 배정 회차, 배정 전(매칭 대기)이면 아직 시작하지 않은 희망 회차 중 가장 이른 회차.
     * 기준 회차가 이미 시작했으면(남은 희망 회차가 없으면) CANCEL_WINDOW_CLOSED.
     */
    private CancelWindow evaluateWindow(Application application) {
        LocalDateTime now = LocalDateTime.now(KOREA);
        GatheringSession base = application.getSession() != null
                ? application.getSession()
                : findCandidateSessions(List.of(application.getId()))
                        .getOrDefault(application.getId(), List.of()).stream()
                        .filter(session -> session.getStartAt().isAfter(now))
                        .min(Comparator.comparing(GatheringSession::getStartAt))
                        .orElseThrow(() -> new CustomException(ErrorCode.CANCEL_WINDOW_CLOSED));
        if (!base.getStartAt().isAfter(now)) {
            throw new CustomException(ErrorCode.CANCEL_WINDOW_CLOSED);
        }
        return CancelPolicy.evaluate(now, base.getEventDate());
    }

    // 신청 ID → 희망 회차(우선순위 순)
    private Map<UUID, List<GatheringSession>> findCandidateSessions(Collection<UUID> applicationIds) {
        return applicationCandidateSessionRepository.findByApplicationIdsWithSession(applicationIds).stream()
                .collect(Collectors.groupingBy(candidate -> candidate.getApplication().getId(),
                        Collectors.mapping(ApplicationCandidateSession::getSession, Collectors.toList())));
    }
}
