package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.client.dto.request.DiningWishUpdateRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse.DepositOutcome;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningPrefillResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.form.client.service.FormService;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.DiningTableService;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private static final Set<MatchStatus> WISH_CHANGEABLE = Set.of(MatchStatus.WAITING, MatchStatus.REALLOCATING);

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
    private final GatheringSessionService gatheringSessionService;
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
        Map<UUID, UnassignedReason> unassignedReasons = matchingService.findLastUnassignedReasons(
                candidates.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(GatheringSession::getId).toList())));
        return DiningApplicationListResponse.builder()
                .applications(applications.stream()
                        .map(a -> DiningApplicationListResponse.Item.of(a,
                                candidates.getOrDefault(a.getId(), List.of()),
                                ticketStatuses.get(a.getId()),
                                tables.get(a.getId()),
                                resolutionIds.get(a.getId()),
                                deposits.get(a.getId()),
                                unassignedReasons.get(a.getId())))
                        .toList())
                .build();
    }

    /**
     * 매칭 대기 중 희망 회차·지역 바꾸기(STA-06, KAN-436). 본인 신청이 WAITING|REALLOCATING일 때만 둘 다 요청 순서(우선순위·지망 순)로 통째로 바꾼다.
     * 새 회차는 같은 모임에서 신청 가능(모집 중·마감 전, 신청과 같은 검사)하고 매칭 실행 전이어야 하며, 그 회차에 내 다른 신청이 있으면 ALREADY_APPLIED.
     * 결제·이용권·보증금은 그대로 둔다. 매칭 실행(회차 잠금)과 겹치지 않게 기존·새 회차를 ID 순으로 잠근 뒤 신청을 잠그고 상태를 다시 본다.
     */
    @Transactional
    public void updateWishes(UUID applicationId, UUID userId, DiningWishUpdateRequest request) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }
        checkWishChangeable(application);
        List<DiningRegion> regions = ApplicationService.resolveCandidateRegions(request.getCandidateRegions());
        List<UUID> sessionIds = request.getCandidateSessionIds().stream().distinct().toList();

        Set<UUID> lockIds = new TreeSet<>(sessionIds);
        findCandidateSessions(List.of(applicationId)).getOrDefault(applicationId, List.of())
                .forEach(session -> lockIds.add(session.getId()));
        Map<UUID, GatheringSession> locked = new HashMap<>();
        lockIds.forEach(id -> gatheringSessionService.findSessionForUpdate(id).ifPresent(s -> locked.put(id, s)));
        applicationRepository.refreshForUpdate(application);
        checkWishChangeable(application);

        LocalDateTime now = LocalDateTime.now();
        List<GatheringSession> sessions = sessionIds.stream().map(id -> {
            GatheringSession session = locked.get(id);
            if (session == null || session.getGathering().getDeletedAt() != null) {
                throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
            }
            if (!session.getGathering().getId().equals(application.getGathering().getId())) {
                throw new CustomException(ErrorCode.SESSION_GATHERING_MISMATCH);
            }
            session.validateApplicable(now);
            return session;
        }).toList();
        if (!matchingService.findSessionIdsWithMatchRun(sessionIds).isEmpty()) {
            throw new CustomException(ErrorCode.WISH_CHANGE_NOT_ALLOWED);
        }

        applicationCandidateSessionRepository.deleteByApplication_Id(applicationId);
        // UNIQUE(application_id, session_id): 새 행 INSERT 전에 DELETE를 내보내고, 남은 내 다른 신청과의 중복을 본다.
        applicationCandidateSessionRepository.flush();
        if (applicationCandidateSessionRepository.existsBySession_IdInAndApplication_User_IdAndApplication_DeletedAtIsNull(
                sessionIds, userId)) {
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }
        for (int i = 0; i < sessions.size(); i++) {
            applicationCandidateSessionRepository.save(new ApplicationCandidateSession(application, sessions.get(i), i + 1));
        }
        application.changeCandidateRegions(regions);
    }

    // 매칭 대기(WAITING|REALLOCATING, 옛 신청의 NULL은 WAITING)인 우연한 식탁 신청만 바꿀 수 있다.
    private static void checkWishChangeable(Application application) {
        if (application.getDeletedAt() != null || application.getStatus() == ApplicationStatus.CANCELLED
                || application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE
                || (application.getMatchStatus() != null && !WISH_CHANGEABLE.contains(application.getMatchStatus()))) {
            throw new CustomException(ErrorCode.WISH_CHANGE_NOT_ALLOWED);
        }
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
     * 취소(설계 4.1, KAN-402, KAN-418, KAN-420). 기준 회차 날짜로 시점을 나눈다. 이용권은 복원하지 않고 돈으로 환불한다.
     * - 테이블(제안·확정)에 앉지 않은 신청(결제 대기·매칭 전·미배정)은 시점과 상관없이 1회분 100% 환불, 보증금 100% 반환,
     *   제재 없음. 매칭되지 않은 건 서비스 책임이다. (KAN-420)
     * 테이블에 앉은 신청:
     * - 마감 전: 1회권 1회분 100% 환불 송금 요청, 보증금 100% 반환, 참석 CANCELED_EARLY.
     * - 마감 후: 1회권 1회분 50%(원 단위 버림) 환불, 보증금 50% 반환(나머지 몰수), 참석 CANCELED_LATE. 제재 없음.
     * - 당일: 노쇼와 동일 취급(환불 없음, 보증금 몰수 + 보증금 대상 지정).
     * - 배정 회차가 운영 사정으로 취소됐으면 시점과 상관없이 보증금 100% 반환, 제재 없음(이용권은 회차 취소 때 복원됨).
     * 멤버십·무료 신청은 이용권 환불이 없다. 이용권 환불이 있으면 refundAccount 필수(REFUND_ACCOUNT_REQUIRED).
     * 보증금은 신청 때 받은 반환 계좌로 돌려준다. 환불 차감 기록은 REFUND_PROCESSING → 송금 완료 시 REFUNDED.
     * 회차가 이미 시작했으면 CANCEL_WINDOW_CLOSED. 테이블에 앉아 있었으면 빼고 재조정한다(설계 4.7).
     */
    @Transactional
    public void cancelApplication(UUID applicationId, UUID userId, RefundAccount refundAccount) {
        Application application = findCancellable(applicationId, userId);
        DiningTable table = matchingService.findTablesByApplicationIds(List.of(applicationId)).get(applicationId);
        // 잠금 순서는 전역으로 회차 → 테이블 → 보증금 → 신청이다(DiningConfirmService.confirm: 회차→테이블→신청,
        // DepositService.markPaid: 보증금→신청). 신청을 바꾸기 전에 이 순서로 잡아야 동시 확정·입금 확인과 교착하지 않는다.
        // 재조정(rebalance)·보증금 처리는 같은 트랜잭션에서 이미 잡은 행을 다시 잠그므로 순서가 바뀌지 않는다.
        if (table != null) {
            diningTableService.lockForUpdate(table.getId());
        }
        depositService.lockForUpdate(applicationId);
        // 신청은 마지막에 잠그고 DB 최신 상태로 다시 검사한다. 동시 취소의 뒤 요청은 여기서 409(이미 취소됨).
        applicationRepository.refreshForUpdate(application);
        checkCancellableStatus(application);
        Settlement settlement = settle(application, table);
        // 아무것도 바꾸기 전에 확인한다.
        if (settlement.ticketRefundAmount() > 0) {
            RefundAccount.requireComplete(refundAccount);
        }

        application.cancel();
        if (settlement.ticketRefundAmount() > 0) {
            ticketService.refundPurchase(application, refundAccount, settlement.refundPercent());
        }
        // 확정 테이블 멤버였다면 참석을 취소로(참석 행이 있을 때만). 재조정이 멤버 행을 removed_at으로 빼기 전에 기록한다. (KAN-349)
        diningAttendanceService.cancelAttendance(applicationId, settlement.window() != CancelWindow.BEFORE_DEADLINE);
        if (settlement.noShow()) {
            depositService.forfeit(applicationId);
            application.getUser().markDepositRequired();
        } else {
            depositService.requestReturn(applicationId, settlement.refundPercent());
        }
        // 테이블(제안·확정)에 앉아 있었다면 빼고, 인원이 모자라면 충원·재배치한다. (설계 4.7)
        if (table != null) {
            diningTableService.rebalance(table.getId());
        }
        eventPublisher.publishEvent(new ApplicationCancelledEvent(application));
    }

    /** 지금 취소하면 적용되는 결과(PAY-02). 취소와 같은 검증·계산을 하고 아무것도 바꾸지 않는다. */
    public DiningCancelPreviewResponse previewCancel(UUID applicationId, UUID userId) {
        Application application = findCancellable(applicationId, userId);
        Settlement settlement = settle(application,
                matchingService.findTablesByApplicationIds(List.of(applicationId)).get(applicationId));
        int ticketRefund = settlement.ticketRefundAmount();
        return new DiningCancelPreviewResponse(settlement.window(), false, ticketRefund > 0,
                settlement.depositOutcome(), settlement.noShow(),
                ticketRefund, settlement.depositReturnAmount(), ticketRefund > 0);
    }

    // 취소 시점·착석·노쇼 여부로 정한 환불 비율(100·50·0)과 그 비율로 나갈 돈. 취소와 미리보기가 같이 쓴다.
    private record Settlement(CancelWindow window, boolean noShow, int refundPercent,
                              int ticketRefundAmount, int depositReturnAmount, DepositOutcome depositOutcome) {
    }

    private Settlement settle(Application application, DiningTable table) {
        CancelWindow window = evaluateWindow(application);
        // 운영 사정으로 회차가 취소됐으면 시점과 상관없이 보증금 전액 반환·제재 없음. 이용권은 회차 취소 때 이미 복원됐다.
        boolean sessionCancelled = application.isSessionCancelled();
        // 테이블에 앉지 않은 신청은 매칭되지 않은 게 서비스 책임이라 시점과 상관없이 100%. (KAN-420)
        boolean seated = table != null;
        boolean noShow = !sessionCancelled && seated && window == CancelWindow.SAME_DAY;
        int percent = sessionCancelled || !seated || window == CancelWindow.BEFORE_DEADLINE ? 100 : noShow ? 0 : 50;
        int ticketRefund = percent > 0 ? ticketService.refundableAmount(application.getId(), percent) : 0;
        Deposit deposit = depositService.findByApplicationIds(List.of(application.getId())).get(application.getId());
        if (deposit == null || deposit.getStatus() != DepositStatus.PAID) {
            // 돌려주거나 몰수할 입금이 없다
            return new Settlement(window, noShow, percent, ticketRefund, 0, DepositOutcome.NONE);
        }
        DepositOutcome outcome = percent == 100 ? DepositOutcome.RETURN
                : percent > 0 ? DepositOutcome.PARTIAL : DepositOutcome.FORFEIT;
        return new Settlement(window, noShow, percent, ticketRefund, deposit.returnAmountFor(percent), outcome);
    }

    // 잠그지 않고 읽는다(미리보기는 읽기 전용 트랜잭션). 취소는 잠근 뒤 checkCancellableStatus를 다시 부른다.
    private Application findCancellable(UUID applicationId, UUID userId) {
        // 취소하면 soft delete되므로 삭제된 신청까지 찾아야 "이미 취소됨(409)"을 구분할 수 있다.
        Application application = applicationRepository.findIncludingDeletedById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getUser() == null || !application.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.APPLICATION_FORBIDDEN);
        }
        checkCancellableStatus(application);
        return application;
    }

    private static void checkCancellableStatus(Application application) {
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
