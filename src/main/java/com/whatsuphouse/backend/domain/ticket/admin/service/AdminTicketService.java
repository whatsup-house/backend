package com.whatsuphouse.backend.domain.ticket.admin.service;

import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminPendingDepositResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.AdminTicketPassResponse;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketProductRequest;
import com.whatsuphouse.backend.domain.ticket.admin.dto.request.TicketAdjustmentRequest;
import com.whatsuphouse.backend.domain.ticket.admin.dto.response.TicketTransactionResponse;
import com.whatsuphouse.backend.domain.ticket.dto.response.TicketProductResponse;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminTicketService {

    private final TicketPassRepository ticketPassRepository;
    private final TicketProductRepository ticketProductRepository;
    private final TicketTransactionRepository ticketTransactionRepository;
    private final TicketService ticketService;

    @Transactional(readOnly = true)
    public List<TicketProductResponse> listProducts() {
        return ticketProductRepository.findAllByDeletedAtIsNullOrderByPriceAscCreatedAtAsc()
                .stream()
                .map(TicketProductResponse::from)
                .toList();
    }

    public TicketProductResponse createProduct(TicketProductRequest request) {
        TicketProductOption product = new TicketProductOption(
                request.getName().trim(), request.getSessionCount(), request.getPrice());
        ticketProductRepository.save(product);
        return TicketProductResponse.from(product);
    }

    public TicketProductResponse updateProduct(UUID productId, TicketProductRequest request) {
        TicketProductOption product = ticketProductRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PRODUCT_NOT_FOUND));
        product.update(request.getName().trim(), request.getSessionCount(), request.getPrice());
        return TicketProductResponse.from(product);
    }

    public void deleteProduct(UUID productId) {
        TicketProductOption product = ticketProductRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.TICKET_PRODUCT_NOT_FOUND));
        product.delete();
    }

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
                .map(pass -> AdminPendingDepositResponse.from(pass, ticketService.resolvePaymentPendingApplication(pass)))
                .toList();
    }

    /** 대시보드 뱃지용 입금 대기 건수. */
    @Transactional(readOnly = true)
    public long countPendingDeposits() {
        return ticketPassRepository.countByStatusAndDeletedAtIsNull(TicketPassStatus.PENDING);
    }

    /** 입금 확인 → 이용권 활성화(잔여 충전). PENDING이 아니면 TICKET_ALREADY_PROCESSED. */
    public AdminTicketPassResponse confirm(UUID passId, UUID adminId) {
        return AdminTicketPassResponse.from(ticketService.settlePass(passId, "ADMIN:" + adminId));
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
