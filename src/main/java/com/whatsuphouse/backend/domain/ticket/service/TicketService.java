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
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.participant.service.ParticipantService;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProduct;
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
    private final ParticipantService participantService;
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

    /** 이용권 구매(선결제) 요청. 입금 확인 전이므로 PENDING으로 생성된다. */
    public TicketPassResponse purchase(UUID userId, TicketProduct product) {
        return purchase(userId, null, product, null);
    }

    public TicketPassResponse purchase(UUID userId, TicketProduct product, UUID applicationId) {
        return purchase(userId, null, product, applicationId);
    }

    public TicketPassResponse purchase(UUID userId, UUID productId, TicketProduct product, UUID applicationId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Participant participant = participantService.getOrCreateForUser(user);
        if (participant.isBlockedFromRandomTable() || !participant.isApprovedForRandomTable()) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        Application application = null;
        if (applicationId != null) {
            application = getMemberPaymentPendingApplication(applicationId, userId, participant);
        }
        TicketPass pass = createPendingPass(participant, application, productId, product);
        if (application != null) {
            eventPublisher.publishEvent(new TicketPurchaseRequestedEvent(application, pass));
        }
        return TicketPassResponse.from(pass);
    }

    /** 승인 메일의 예약번호로 비회원 이용권 구매 요청을 생성한다. */
    public TicketPassResponse purchaseGuest(String bookingNumber, TicketProduct product) {
        return purchaseGuest(bookingNumber, null, product);
    }

    public TicketPassResponse purchaseGuest(String bookingNumber, UUID productId, TicketProduct product) {
        Application application = getGuestApplication(bookingNumber);
        if (application.getStatus() != ApplicationStatus.PAYMENT_PENDING) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        TicketPass pass = createPendingPass(application.getParticipant(), application, productId, product);
        eventPublisher.publishEvent(new TicketPurchaseRequestedEvent(application, pass));
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
        Participant participant = participantService.getOrCreateForUser(user);
        List<TicketPass> passes = ticketPassRepository.findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        UUID gatheringId = null;
        ApplicationStatus applicationStatus = null;
        if (applicationId != null) {
            Application application = getMemberPaymentPendingOrConfirmedApplication(applicationId, userId);
            gatheringId = application.getGathering().getId();
            applicationStatus = application.getStatus();
        }
        return buildTicketsResponse(participant, passes, applicationId, gatheringId, applicationStatus);
    }

    /** 승인 메일의 예약번호로 비회원 자격과 이용권을 조회한다. */
    @Transactional(readOnly = true)
    public MyTicketsResponse getGuestTickets(String bookingNumber) {
        Application application = getGuestApplication(bookingNumber);
        if (application.getStatus() != ApplicationStatus.PAYMENT_PENDING
                && application.getStatus() != ApplicationStatus.CONFIRMED
                && application.getStatus() != ApplicationStatus.ATTENDED) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        Participant participant = application.getParticipant();
        List<TicketPass> passes = ticketPassRepository
                .findByParticipant_IdAndDeletedAtIsNullOrderByCreatedAtDesc(participant.getId());
        return buildTicketsResponse(participant, passes, application.getId(), application.getGathering().getId(), application.getStatus());
    }

    private Application getMemberPaymentPendingApplication(UUID applicationId, UUID userId, Participant participant) {
        Application application = getMemberPaymentPendingOrConfirmedApplication(applicationId, userId);
        if (application.getStatus() != ApplicationStatus.PAYMENT_PENDING
                || application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE
                || application.getParticipant() == null
                || !application.getParticipant().getId().equals(participant.getId())) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        return application;
    }

    private Application getMemberPaymentPendingOrConfirmedApplication(UUID applicationId, UUID userId) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        if (application.getParticipant() == null
                || application.getParticipant().getUser() == null
                || !application.getParticipant().getUser().getId().equals(userId)
                || application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        return application;
    }

    private Application getGuestApplication(String bookingNumber) {
        Application application = applicationRepository.findByBookingNumberAndDeletedAtIsNull(bookingNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        Participant participant = application.getParticipant();
        if (participant == null || participant.getUser() != null
                || application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE
                || !participant.isApprovedForRandomTable()
                || participant.isBlockedFromRandomTable()) {
            throw new CustomException(ErrorCode.TICKET_PURCHASE_NOT_ALLOWED);
        }
        return application;
    }

    private TicketPass createPendingPass(
            Participant participant, Application application, UUID productId, TicketProduct legacyProduct) {
        TicketPass pass;
        if (productId != null) {
            TicketProductOption productOption = ticketProductRepository.findByIdAndDeletedAtIsNull(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PRODUCT_NOT_FOUND));
            pass = new TicketPass(participant, application, productOption);
        } else if (legacyProduct != null) {
            pass = TicketPass.builder()
                    .participant(participant)
                    .application(application)
                    .product(legacyProduct)
                    .build();
        } else {
            throw new CustomException(ErrorCode.TICKET_PRODUCT_NOT_FOUND);
        }
        ticketPassRepository.save(pass);
        return pass;
    }

    private MyTicketsResponse buildTicketsResponse(
            Participant participant, List<TicketPass> passes, UUID applicationId, UUID gatheringId,
            ApplicationStatus applicationStatus) {
        int totalRemaining = passes.stream()
                .filter(p -> p.getStatus() == TicketPassStatus.ACTIVE)
                .mapToInt(TicketPass::getRemainingCount)
                .sum();
        List<TicketPassResponse> items = passes.stream()
                .map(TicketPassResponse::from)
                .toList();
        return MyTicketsResponse.builder()
                .accountStatus(participant.getAccountStatus())
                .randomTableEligibility(participant.getRandomTableEligibility())
                .purchasable(participant.isApprovedForRandomTable() && !participant.isBlockedFromRandomTable())
                .totalRemaining(totalRemaining)
                .passes(items)
                .applicationId(applicationId)
                .gatheringId(gatheringId)
                .applicationStatus(applicationStatus)
                .build();
    }

    /** 우연한 식탁 신청 시 1회 차감한다. 사용 가능한 이용권이 없으면 NO_AVAILABLE_TICKET. */
    public void useOneTicket(User user) {
        Participant participant = participantService.getOrCreateForUser(user);
        if (!tryUseOneTicket(participant)) {
            throw new CustomException(ErrorCode.NO_AVAILABLE_TICKET);
        }
    }

    /** 참가자 기준으로 잔여 이용권을 1회 차감한다. 이용권이 없으면 상태 변경 없이 false를 반환한다. */
    public boolean tryUseOneTicket(Participant participant) {
        return tryUseOneTicket(participant, null);
    }

    public boolean tryUseOneTicket(Participant participant, Application application) {
        if (application != null && ticketTransactionRepository.existsByApplication_IdAndTransactionType(
                application.getId(), TicketTransactionType.USE)) {
            return true;
        }
        List<TicketPass> usable = ticketPassRepository.findUsableByParticipantForUpdate(
                participant.getId(), TicketPassStatus.ACTIVE, PageRequest.of(0, 1));
        if (usable.isEmpty()) {
            return false;
        }
        TicketPass pass = usable.get(0);
        pass.deductOne();
        ticketTransactionRepository.save(TicketTransaction.of(
                pass, application, TicketTransactionType.USE, -1, "우연한 식탁 참가 확정"));
        return true;
    }

    /** 우연한 식탁 신청 취소 시 차감했던 이용권을 1회 환불한다. 환불 대상이 없으면 무시. */
    public void refundOneTicket(User user) {
        ticketPassRepository.findByParticipant_User_IdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId()).stream()
                .filter(p -> p.getStatus() == TicketPassStatus.USED_UP
                        || (p.getStatus() == TicketPassStatus.ACTIVE && p.getRemainingCount() < p.getTotalCount()))
                .findFirst()
                .ifPresent(TicketPass::refundOne);
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
