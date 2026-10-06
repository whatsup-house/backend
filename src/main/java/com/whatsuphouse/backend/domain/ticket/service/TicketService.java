package com.whatsuphouse.backend.domain.ticket.service;

import com.whatsuphouse.backend.domain.ticket.dto.response.MyTicketsResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketProductResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.notification.event.TicketPurchaseRequestedEvent;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<TicketProductResponse> listProducts() {
        return ticketProductRepository.findAllByDeletedAtIsNullOrderBySessionCountAscPriceAscCreatedAtAsc()
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

    /** 회원 기준으로 잔여 이용권을 1회 차감한다. 이용권이 없으면 상태 변경 없이 false를 반환한다. */
    public boolean tryUseOneTicket(User user, Application application) {
        if (application != null && ticketTransactionRepository.existsByApplication_IdAndTransactionType(
                application.getId(), TicketTransactionType.USE)) {
            return true;
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
                    pass.refundOne();
                    ticketTransactionRepository.save(TicketTransaction.of(
                            pass, application, TicketTransactionType.REFUND, 1, "신청 취소 복구"));
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
