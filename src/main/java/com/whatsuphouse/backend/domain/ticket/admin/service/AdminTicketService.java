package com.whatsuphouse.backend.domain.ticket.admin.service;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminPendingDepositResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketAdjustmentRequest;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.TicketTransactionResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminTicketService {

    private final TicketPassRepository ticketPassRepository;
    private final TicketTransactionRepository ticketTransactionRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AdminTicketPassResponse> listPending() {
        return ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING)
                .stream()
                .map(AdminTicketPassResponse::from)
                .toList();
    }

    /**
     * 입금 대기 큐. 게더링을 가로질러 요청 오래된 순(FIFO)으로 PENDING 이용권을 나열하고,
     * 연결된 PAYMENT_PENDING 신청(신청자·게더링)을 붙여 빠른 입금 확인을 돕는다.
     */
    @Transactional(readOnly = true)
    public List<AdminPendingDepositResponse> listPendingDeposits() {
        return ticketPassRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus.PENDING)
                .stream()
                .map(pass -> AdminPendingDepositResponse.from(pass, resolvePaymentPendingApplication(pass)))
                .toList();
    }

    /** 대시보드 뱃지용 입금 대기 건수. */
    @Transactional(readOnly = true)
    public long countPendingDeposits() {
        return ticketPassRepository.countByStatusAndDeletedAtIsNull(TicketPassStatus.PENDING);
    }

    /** 입금 확인 → 이용권 활성화(잔여 충전). PENDING이 아니면 TICKET_ALREADY_PROCESSED. */
    public AdminTicketPassResponse confirm(UUID passId) {
        TicketPass pass = ticketPassRepository.findByIdAndDeletedAtIsNull(passId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PASS_NOT_FOUND));
        Application paymentPending = resolvePaymentPendingApplication(pass);
        if (paymentPending != null) {
            int occupied = applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(
                    paymentPending.getGathering().getId(), ApplicationStatus.SEAT_OCCUPYING);
            if (occupied >= paymentPending.getGathering().getMaxAttendees()) {
                throw new CustomException(ErrorCode.GATHERING_FULL);
            }
        }
        pass.activate();
        ticketTransactionRepository.save(TicketTransaction.of(
                pass, null, TicketTransactionType.ISSUE, pass.getTotalCount(), "입금 확인 및 이용권 발급"));
        if (paymentPending != null) {
            pass.deductOne();
            ticketTransactionRepository.save(TicketTransaction.of(
                    pass, paymentPending, TicketTransactionType.USE, -1, "이용권 구매 신청 자동 확정"));
            paymentPending.confirmPayment();
            paymentPending.confirm();
            eventPublisher.publishEvent(new ApplicationConfirmedEvent(paymentPending));
        }
        return AdminTicketPassResponse.from(pass);
    }

    private Application resolvePaymentPendingApplication(TicketPass pass) {
        Application linked = pass.getApplication();
        if (linked != null) {
            return linked.getStatus() == ApplicationStatus.PAYMENT_PENDING ? linked : null;
        }
        return applicationRepository
                .findFirstByParticipant_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                        pass.getParticipant().getId(), ApplicationStatus.PAYMENT_PENDING)
                .orElse(null);
    }

    public AdminTicketPassResponse adjust(UUID passId, TicketAdjustmentRequest request) {
        TicketPass pass = ticketPassRepository.findByIdAndDeletedAtIsNull(passId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PASS_NOT_FOUND));
        int quantity = request.getQuantity() != null ? request.getQuantity() : 0;
        pass.adjustRemaining(quantity);
        TicketTransactionType type = quantity > 0
                ? TicketTransactionType.ADMIN_ADD : TicketTransactionType.ADMIN_DEDUCT;
        ticketTransactionRepository.save(TicketTransaction.of(
                pass, null, type, quantity, request.getReason()));
        return AdminTicketPassResponse.from(pass);
    }

    @Transactional(readOnly = true)
    public List<TicketTransactionResponse> getTransactions(UUID passId) {
        if (!ticketPassRepository.existsById(passId)) {
            throw new CustomException(ErrorCode.TICKET_PASS_NOT_FOUND);
        }
        return ticketTransactionRepository.findByTicketPass_IdOrderByCreatedAtDesc(passId)
                .stream().map(TicketTransactionResponse::from).toList();
    }
}
