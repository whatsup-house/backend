package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketProductResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.application.client.service.MembershipWeekRule;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringSessionService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.TicketPurchaseRequestedEvent;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    private final TicketPassRepository ticketPassRepository;
    private final UserRepository userRepository;
    private final TicketTransactionRepository ticketTransactionRepository;
    private final TicketProductRepository ticketProductRepository;
    private final ApplicationRepository applicationRepository;
    private final GatheringSessionService gatheringSessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final DepositService depositService;
    private final MembershipWeekRule membershipWeekRule;
    private final ExceptionCaseService exceptionCaseService;

    // 멤버십으로 확정한 신청이 매칭에 실패하면 기간을 이만큼 연장한다. (KAN-401)
    private static final int MEMBERSHIP_FAILURE_EXTENSION_DAYS = 7;

    @Transactional(readOnly = true)
    public List<TicketProductResponse> listProducts() {
        return ticketProductRepository.findAllByDeletedAtIsNullOrderByPriceAscCreatedAtAsc()
                .stream()
                .map(TicketProductResponse::from)
                .toList();
    }

    /** 이용권 구매(선결제) 요청. 입금 확인 전이므로 PENDING으로 생성된다. 회원 전용. */
    public TicketPassResponse purchase(UUID userId, UUID productId, UUID applicationId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.isBlockedFromRandomTable() || !user.isApprovedForRandomTable()) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        Application application = null;
        if (applicationId != null) {
            application = getMemberPaymentPendingApplication(applicationId, userId);
        }
        TicketPass pass = createPendingPass(user, application, productId);
        if (application != null) {
            eventPublisher.publishEvent(new TicketPurchaseRequestedEvent(application, pass));
        }
        return TicketPassResponse.from(pass);
    }

    /**
     * 입금 확인 → 이용권 활성화(잔여 충전). 연결된 결제 대기 신청이 있으면 1회 차감 후 자동 확정한다.
     * 보증금 대상 신청은 보증금 입금 전이면 차감만 하고 결제 대기로 둔다(확정은 DepositService.markPaid). (KAN-402)
     * 멤버십이 그 신청의 주 1회·기간 조건을 어기면 이용권만 활성화하고 신청은 결제 대기로 둔 채 예외함(CONFLICT)에 올린다.
     * PENDING이 아니면 TICKET_ALREADY_PROCESSED. actor는 거래 메모에 남는다("ADMIN:{id}", "WEBHOOK", "SIMULATOR").
     */
    public TicketPass settlePass(UUID passId, String actor) {
        TicketPass pass = ticketPassRepository.findByIdAndDeletedAtIsNullForUpdate(passId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PASS_NOT_FOUND));
        Application paymentPending = resolvePaymentPendingApplication(pass);
        // 이미 이용권을 쓴(보증금만 기다리는) 신청에서 또 차감하지 않는다.
        // ponytail: 연결 없는 이용권이면 그 회원의 다른 결제 대기 신청은 찾지 않는다. 필요하면 USE 없는 신청을 고르도록 조회를 바꾼다.
        if (paymentPending != null && hasTicketUse(paymentPending.getId())) {
            paymentPending = null;
        }
        // 회차 배정 전(우연한 식탁 매칭 전) 신청은 매칭이 회차별 인원을 정하므로 정원을 세지 않는다. (KAN-338)
        if (paymentPending != null && paymentPending.getSession() != null) {
            // 동시 승인·신청과의 정원 경쟁을 막기 위해 회차 행을 잠근 뒤 센다.
            gatheringSessionService.findSessionForUpdate(paymentPending.getSession().getId());
            int occupied = applicationRepository.countBySession_IdAndStatusInAndDeletedAtIsNull(
                    paymentPending.getSession().getId(), ApplicationStatus.SEAT_OCCUPYING);
            if (occupied >= paymentPending.getSession().getMaxAttendees()) {
                throw new CustomException(ErrorCode.GATHERING_FULL);
            }
        }
        pass.activate();
        ticketTransactionRepository.save(TicketTransaction.of(
                pass, null, TicketTransactionType.ISSUE, pass.getTotalCount(), "입금 확인 및 이용권 발급 (" + actor + ")"));
        if (paymentPending != null && pass.isPeriod()) {
            String violation = findMembershipViolation(pass, paymentPending);
            if (violation != null) {
                exceptionCaseService.open(ExceptionCaseType.CONFLICT, null, null, paymentPending.getId(),
                        "멤버십 조건 불충족: " + violation);
                paymentPending = null;
            }
        }
        if (paymentPending != null) {
            // 멤버십은 횟수를 깎지 않고 수량 0 USE로 사용 기록만 남긴다(취소 복원·연장의 기준).
            int quantity = pass.isPeriod() ? 0 : -1;
            if (!pass.isPeriod()) {
                pass.deductOne();
            }
            ticketTransactionRepository.save(TicketTransaction.of(
                    pass, paymentPending, TicketTransactionType.USE, quantity, "이용권 구매 신청 자동 확정 (" + actor + ")"));
            paymentPending.confirmPayment();
            if (depositService.isSatisfied(paymentPending.getId())) {
                paymentPending.confirm();
                // @Async 확정 메일이 트랜잭션 밖에서 모임 제목(종류)을 읽으므로 여기서 로드해 둔다.
                Hibernate.initialize(paymentPending.getGathering());
                eventPublisher.publishEvent(new ApplicationConfirmedEvent(paymentPending));
            }
        }
        return pass;
    }

    // 신청 경로와 같은 멤버십 조건: 마지막 희망 회차가 기간 안, 희망 회차가 한 주, 그 주에 다른 활성 신청 없음. 위반 사유 또는 null.
    private String findMembershipViolation(TicketPass pass, Application application) {
        List<LocalDate> dates = membershipWeekRule.candidateDates(application.getId());
        LocalDate latest = dates.stream().max(Comparator.naturalOrder()).orElse(null);
        if (latest != null && !pass.isUsableOn(latest)) {
            return "희망 회차(" + latest + ")가 멤버십 기간(~" + pass.getValidUntil() + ") 밖입니다.";
        }
        return membershipWeekRule.check(application.getUser().getId(), application.getId(), dates)
                .map(ErrorCode::getMessage)
                .orElse(null);
    }

    /** 이용권에 딸린 결제 대기(PAYMENT_PENDING) 신청. 연결 신청이 없으면 회원의 가장 오래된 결제 대기 신청. 없으면 null. */
    @Transactional(readOnly = true)
    public Application resolvePaymentPendingApplication(TicketPass pass) {
        Application linked = pass.getApplication();
        if (linked != null) {
            return linked.getStatus() == ApplicationStatus.PAYMENT_PENDING ? linked : null;
        }
        if (pass.getUser() == null) {
            return null;    // 레거시 비회원 이용권: 자동 확정 대상 신청 매칭 없음
        }
        return applicationRepository
                .findFirstByUser_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                        pass.getUser().getId(), ApplicationStatus.PAYMENT_PENDING)
                .orElse(null);
    }

    /** 입금 대사 후보: 입금 기한이 지나지 않은 PENDING 이용권. 소유자가 없는 레거시 이용권은 입금자 이름이 없어 제외한다. */
    @Transactional(readOnly = true)
    public List<PendingPayment> listPendingPasses() {
        LocalDateTime now = LocalDateTime.now();
        return ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING)
                .stream()
                .filter(pass -> pass.getUser() != null
                        && (pass.getPaymentDeadline() == null || pass.getPaymentDeadline().isAfter(now)))
                .map(pass -> new PendingPayment(
                        PaymentTargetType.TICKET_PASS, pass.getId(),
                        pass.getApplication() != null ? pass.getApplication().getId() : null,
                        pass.getUser().getName(), pass.getPurchaseAmount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MyTicketsResponse getMyTickets(UUID userId) {
        return getMyTickets(userId, null);
    }

    @Transactional(readOnly = true)
    public MyTicketsResponse getMyTickets(UUID userId, UUID applicationId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        List<TicketPass> passes = ticketPassRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        UUID gatheringId = null;
        ApplicationStatus applicationStatus = null;
        String bookingNumber = null;
        if (applicationId != null) {
            Application application = getMemberPaymentPendingOrConfirmedApplication(applicationId, userId);
            // 기존 API 호환: 결제 후 이동할 모임 페이지는 회차 ID로 연다. 회차 배정 전이면 종류 ID. (KAN-337, KAN-338)
            gatheringId = application.getLegacyGatheringId();
            applicationStatus = application.getStatus();
            bookingNumber = application.getBookingNumber();
        }
        return buildTicketsResponse(user, passes, applicationId, bookingNumber, gatheringId, applicationStatus);
    }

    private Application getMemberPaymentPendingApplication(UUID applicationId, UUID userId) {
        Application application = getMemberPaymentPendingOrConfirmedApplication(applicationId, userId);
        if (application.getStatus() != ApplicationStatus.PAYMENT_PENDING) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        return application;
    }

    private Application getMemberPaymentPendingOrConfirmedApplication(UUID applicationId, UUID userId) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getUser() == null
                || !application.getUser().getId().equals(userId)
                || application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        return application;
    }

    private TicketPass createPendingPass(User user, Application application, UUID productId) {
        if (productId == null) {
            throw new CustomException(ErrorCode.TICKET_PRODUCT_NOT_FOUND);
        }
        TicketProductOption productOption = ticketProductRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PRODUCT_NOT_FOUND));
        TicketPass pass = new TicketPass(user, application, productOption);
        ticketPassRepository.save(pass);
        return pass;
    }

    private MyTicketsResponse buildTicketsResponse(
            User user, List<TicketPass> passes, UUID applicationId, String bookingNumber, UUID gatheringId,
            ApplicationStatus applicationStatus) {
        int totalRemaining = passes.stream()
                .filter(p -> p.getStatus() == TicketPassStatus.ACTIVE)
                .mapToInt(TicketPass::getRemainingCount)
                .sum();
        List<TicketPassResponse> items = passes.stream()
                .map(TicketPassResponse::from)
                .toList();
        return MyTicketsResponse.builder()
                .accountStatus(user.getEffectiveAccountStatus())
                .randomTableEligibility(user.getRandomTableEligibility())
                .purchasable(user.isApprovedForRandomTable() && !user.isBlockedFromRandomTable())
                .totalRemaining(totalRemaining)
                .passes(items)
                .applicationId(applicationId)
                .bookingNumber(bookingNumber)
                .gatheringId(gatheringId)
                .applicationStatus(applicationStatus)
                .build();
    }

    /** 배정 회차가 있으면 그 날짜 기준으로 {@link #tryUseOneTicket(User, Application, LocalDate)}. 없으면 횟수권만 본다. */
    public boolean tryUseOneTicket(User user, Application application) {
        LocalDate sessionDate = application != null && application.getSession() != null
                ? application.getSession().getEventDate() : null;
        return tryUseOneTicket(user, application, sessionDate);
    }

    /**
     * 회원 기준으로 이용권을 1회 사용한다. sessionDate를 덮는 멤버십이 있으면 멤버십을 우선 쓰고(수량 0 USE),
     * 없으면 횟수권을 1회 차감한다. 둘 다 없으면 상태 변경 없이 false. (KAN-401)
     */
    public boolean tryUseOneTicket(User user, Application application, LocalDate sessionDate) {
        if (application != null && ticketTransactionRepository.existsByApplication_IdAndTransactionType(
                application.getId(), TicketTransactionType.USE)) {
            return true;
        }
        if (sessionDate != null) {
            Optional<TicketPass> membership = findMembershipFor(user.getId(), sessionDate);
            if (membership.isPresent()) {
                ticketTransactionRepository.save(TicketTransaction.of(
                        membership.get(), application, TicketTransactionType.USE, 0, "우연한 식탁 참가 확정 (멤버십)"));
                return true;
            }
        }
        List<TicketPass> usable = ticketPassRepository.findUsableByUserForUpdate(
                user.getId(), TicketPassStatus.ACTIVE, PageRequest.of(0, 1));
        if (usable.isEmpty()) {
            return false;
        }
        TicketPass pass = usable.get(0);
        pass.deductOne();
        ticketTransactionRepository.save(TicketTransaction.of(
                pass, application, TicketTransactionType.USE, -1, "우연한 식탁 참가 확정"));
        return true;
    }

    /** 이 신청에 아직 차감 상태인 이용권 사용(USE) 기록이 있는가(멤버십 USE(0) 포함). 복원·환불된 USE는 세지 않는다. */
    @Transactional(readOnly = true)
    public boolean hasTicketUse(UUID applicationId) {
        return ticketTransactionRepository
                .findByApplication_IdInAndTransactionType(List.of(applicationId), TicketTransactionType.USE).stream()
                .anyMatch(TicketTransaction::isDeducted);
    }

    /** 이 신청의 마지막 USE가 아직 차감 상태인 횟수권(1회권) 사용인가. 취소 미리보기의 '1회권 복원' 판단용. (KAN-402) */
    @Transactional(readOnly = true)
    public boolean hasCountDeduction(UUID applicationId) {
        return ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(applicationId, TicketTransactionType.USE)
                .filter(TicketTransaction::isDeducted)
                .filter(use -> !use.getTicketPass().isPeriod())
                .isPresent();
    }

    /** 이 회차 날짜를 덮는 활성 멤버십이 있는가. 신청 도메인의 주 1회 검사 진입 조건. */
    public boolean hasMembershipFor(UUID userId, LocalDate sessionDate) {
        return findMembershipFor(userId, sessionDate).isPresent();
    }

    private Optional<TicketPass> findMembershipFor(UUID userId, LocalDate sessionDate) {
        return ticketPassRepository
                .findMembershipsByUserForUpdate(userId, TicketPassStatus.ACTIVE, TicketProductKind.PERIOD).stream()
                .filter(pass -> pass.isUsableOn(sessionDate))
                .findFirst();
    }

    /**
     * 매칭 실패 보상: 신청을 멤버십으로 확정했으면 만료일을 7일 늘리고 EXTEND 거래(수량 0)를 남긴다.
     * 같은 신청에 EXTEND가 이미 있으면 무시한다(멱등). 횟수권 신청은 대상 아님.
     */
    public void extendMembershipForFailure(UUID applicationId) {
        if (ticketTransactionRepository.existsByApplication_IdAndTransactionType(applicationId, TicketTransactionType.EXTEND)) {
            return;
        }
        ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(applicationId, TicketTransactionType.USE)
                .filter(use -> use.getTicketPass().isPeriod())
                .ifPresent(use -> {
                    TicketPass pass = use.getTicketPass();
                    pass.extend(MEMBERSHIP_FAILURE_EXTENSION_DAYS);
                    ticketTransactionRepository.save(TicketTransaction.of(pass, use.getApplication(),
                            TicketTransactionType.EXTEND, 0, "매칭 실패 멤버십 연장 +" + MEMBERSHIP_FAILURE_EXTENSION_DAYS + "일"));
                });
    }

    /** 만료일이 today 이전인 활성 멤버십을 USED_UP으로 바꾸고 EXPIRE 거래(수량 0)를 남긴다. 처리 건수를 돌려준다. */
    public int expireMemberships(LocalDate today) {
        List<TicketPass> expired = ticketPassRepository.findByStatusAndProductOption_KindAndValidUntilBeforeAndDeletedAtIsNull(
                TicketPassStatus.ACTIVE, TicketProductKind.PERIOD, today);
        expired.forEach(pass -> {
            pass.expire();
            ticketTransactionRepository.save(TicketTransaction.of(
                    pass, null, TicketTransactionType.EXPIRE, 0, "멤버십 기간 만료"));
        });
        return expired.size();
    }

    // 신청에 기록된 마지막 USE 거래 직후 잔여 수. 신청 조회 응답용. (KAN-393)
    @Transactional(readOnly = true)
    public Optional<Integer> findBalanceAfterUse(UUID applicationId) {
        return ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(applicationId, TicketTransactionType.USE)
                .map(TicketTransaction::getBalanceAfter);
    }

    /**
     * 신청에 기록된 USE 거래를 기준으로 1회 복구한다. 동일 신청 중복 복구는 무시한다.
     * 이미 환불(REFUND_*) 절차에 들어간 차감은 돈으로 돌려주므로 이용권까지 복구하지 않는다. (KAN-347)
     */
    public void refundOneTicket(Application application) {
        if (ticketTransactionRepository.existsByApplication_IdAndTransactionType(
                application.getId(), TicketTransactionType.REFUND)) {
            return;
        }
        ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                        application.getId(), TicketTransactionType.USE)
                .filter(TicketTransaction::isDeducted)
                .ifPresent(use -> {
                    use.restore();
                    TicketPass pass = use.getTicketPass();
                    pass.refundOne();   // 멤버십은 횟수가 없어 잔여 그대로
                    ticketTransactionRepository.save(TicketTransaction.of(
                            pass, application, TicketTransactionType.REFUND, pass.isPeriod() ? 0 : 1, "신청 취소 복구"));
                });
    }

    /**
     * 이용권 구매 건 환불(모의, 즉시): 신청의 차감(USE) 기록을 REFUND_REQUESTED → REFUND_PROCESSING → REFUNDED로 넘긴다.
     * 차감한 1회분을 돈으로 돌려주는 것이라 잔여 수는 바꾸지 않는다. 전환에 실패하면 REFUND_FAILED로 남긴다.
     * 차감 기록이 없거나 이미 복원·환불 처리돼 구매 건을 특정할 수 없으면 빈 값. (설계 4.8, KAN-347)
     */
    public Optional<TicketDeductionStatus> refundPurchase(Application application) {
        Optional<TicketTransaction> deduction = ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(application.getId(), TicketTransactionType.USE)
                .filter(TicketTransaction::isDeducted);
        if (deduction.isEmpty()) {
            return Optional.empty();
        }
        TicketTransaction use = deduction.get();
        // ponytail: 모의 PG라 세 단계를 한 번에 넘긴다. 실결제 연동 시 PROCESSING에서 PG 결과(웹훅)를 기다린다.
        try {
            use.advanceRefund(TicketDeductionStatus.REFUND_REQUESTED);
            use.advanceRefund(TicketDeductionStatus.REFUND_PROCESSING);
            use.advanceRefund(TicketDeductionStatus.REFUNDED);
        } catch (CustomException e) {
            use.failRefund();
        }
        return Optional.of(use.getStatus());
    }

    /**
     * 신청별 이용권 차감 상태(가장 최근 USE 거래 기준). 차감 기록이 없는 신청은 결과에 없다.
     * V8 백필 전 USE 행은 status가 비어 있을 수 있어 DEDUCTED로 본다. (KAN-342)
     */
    @Transactional(readOnly = true)
    public Map<UUID, TicketDeductionStatus> findDeductionStatuses(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return ticketTransactionRepository
                .findByApplication_IdInAndTransactionType(applicationIds, TicketTransactionType.USE).stream()
                .sorted(Comparator.comparing(TicketTransaction::getCreatedAt))
                .collect(Collectors.toMap(use -> use.getApplication().getId(),
                        use -> Objects.requireNonNullElse(use.getStatus(), TicketDeductionStatus.DEDUCTED),
                        (older, newer) -> newer));
    }
}
