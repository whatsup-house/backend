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

import java.util.List;
import java.util.UUID;

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
            gatheringId = application.getGathering().getId();
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

    /** 신청에 기록된 USE 거래를 기준으로 1회 복구한다. 동일 신청 중복 복구는 무시한다. */
    public void refundOneTicket(Application application) {
        if (ticketTransactionRepository.existsByApplication_IdAndTransactionType(
                application.getId(), TicketTransactionType.REFUND)) {
            return;
        }
        ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(
                        application.getId(), TicketTransactionType.USE)
                .ifPresent(use -> {
                    TicketPass pass = use.getTicketPass();
                    pass.refundOne();
                    ticketTransactionRepository.save(TicketTransaction.of(
                            pass, application, TicketTransactionType.REFUND, 1, "신청 취소 복구"));
                });
    }
}
