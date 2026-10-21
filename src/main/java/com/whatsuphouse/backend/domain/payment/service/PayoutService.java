package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.payment.dto.response.AdminPayoutResponse;
import com.whatsuphouse.backend.domain.payment.entity.PayoutRequest;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.event.PayoutSentEvent;
import com.whatsuphouse.backend.domain.payment.repository.PayoutRequestRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 운영자 송금 목록(설계 3.3·4.3, KAN-404). 환불·보증금 반환은 돈이 실제로 나가기 전까지 REQUESTED로 두고,
 * 운영자가 이체 후 SENT로 바꾸면 PayoutSentEvent로 후속 상태(REFUNDED·RETURNED)를 맞춘다. 데모 회원 건은 요청 즉시 SENT.
 * 계좌번호는 로그에 남기지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    /** 심사용 회차 초기화(KAN-442): 이 신청·이용권·보증금에 걸린 송금 요청을 지운다(송금 여부와 무관). */
    public void deleteLinkedTo(Collection<UUID> applicationIds, Collection<UUID> ticketPassIds, Collection<UUID> depositIds) {
        payoutRequestRepository.deleteLinkedTo(applicationIds, ticketPassIds, depositIds);
    }

    /** 송금 요청을 만든다. 환불은 계좌 필수(REFUND_ACCOUNT_REQUIRED). 데모 회원이면 바로 송금 완료 처리한다. */
    public PayoutRequest request(PayoutType type, UUID userId, int amount, RefundAccount account,
                                 UUID applicationId, UUID ticketPassId, UUID depositId) {
        if (type == PayoutType.REFUND) {
            RefundAccount.requireComplete(account);
        }
        // 탈퇴한 회원에게도 돌려줄 돈은 돌려준다(탈퇴 회원 포함 조회).
        User user = userService.findUsersByIds(List.of(userId)).get(userId);
        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        PayoutRequest payout = payoutRequestRepository.save(
                PayoutRequest.request(user, type, amount, account, applicationId, ticketPassId, depositId));
        // 데모 회원은 실제 이체 없이 바로 완료로 보여 준다(설계 14장).
        if (user.isDemo()) {
            send(payout, null);
        }
        return payout;
    }

    /** 운영자 송금 완료. 건마다 잠근 뒤 최신 상태로 REQUESTED만 SENT로 바꾼다(이미 SENT면 무시). */
    public void markSent(List<UUID> ids, UUID adminId) {
        // 같은 순서로 잠가야 두 운영자가 겹치는 목록을 동시에 처리해도 교착하지 않는다.
        for (UUID id : ids.stream().distinct().sorted().toList()) {
            PayoutRequest payout = payoutRequestRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new CustomException(ErrorCode.PAYOUT_NOT_FOUND));
            payoutRequestRepository.refreshForUpdate(payout);
            send(payout, adminId);
        }
    }

    @Transactional(readOnly = true)
    public List<AdminPayoutResponse> list(PayoutType type, PayoutStatus status) {
        return payoutRequestRepository.findByTypeAndStatusOrderByCreatedAtAsc(type, status).stream()
                .map(AdminPayoutResponse::from)
                .toList();
    }

    /** 이용권 ID → 환불 송금 상태. 환불 요청이 없는 이용권은 결과에 없다. (내 이용권 화면) */
    @Transactional(readOnly = true)
    public Map<UUID, PayoutStatus> findRefundStatusesByPassIds(Collection<UUID> ticketPassIds) {
        if (ticketPassIds.isEmpty()) {
            return Map.of();
        }
        return payoutRequestRepository.findByTicketPassIdInAndType(ticketPassIds, PayoutType.REFUND).stream()
                .collect(Collectors.toMap(PayoutRequest::getTicketPassId, PayoutRequest::getStatus, (first, second) -> first));
    }

    /** 반환 대기 보증금이 몰수되면 아직 송금 전인 반환 요청을 거둔다. 이미 송금했으면 그대로. */
    public void withdrawDepositReturn(UUID depositId) {
        payoutRequestRepository.findByDepositId(depositId)
                .filter(payout -> payout.getStatus() == PayoutStatus.REQUESTED)
                .ifPresent(payoutRequestRepository::delete);
    }

    /** 송금이 threshold 전에 끝난 건의 계좌번호를 가린다. 보증금 반환 건의 보증금 ID를 돌려준다(보증금 쪽 사본도 가리도록). */
    public List<UUID> maskAccountsSentBefore(LocalDateTime threshold) {
        List<PayoutRequest> payouts = payoutRequestRepository
                .findByStatusAndAccountMaskedFalseAndSentAtBefore(PayoutStatus.SENT, threshold);
        payouts.forEach(PayoutRequest::maskAccount);
        return payouts.stream().map(PayoutRequest::getDepositId).filter(Objects::nonNull).toList();
    }

    private void send(PayoutRequest payout, UUID adminId) {
        if (payout.markSent(adminId, LocalDateTime.now())) {
            eventPublisher.publishEvent(new PayoutSentEvent(
                    payout.getId(), payout.getType(), payout.getApplicationId(), payout.getDepositId()));
        }
    }
}
