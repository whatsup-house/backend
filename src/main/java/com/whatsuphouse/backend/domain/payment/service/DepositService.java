package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.event.ApplicationStatusChangedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 노쇼 보증금(설계 4.3, KAN-402). 보증금 대상 회원의 신청은 보증금 입금과 이용권 사용이 모두 끝나야 확정되고 매칭 후보가 된다.
 * 체크인·사전 취소 → 반환 대기, 노쇼 확정·당일 취소 → 몰수. 보증금이 없는 신청은 모든 조건을 충족한 것으로 본다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DepositService {

    private final DepositRepository depositRepository;
    // TicketService.settlePass가 isSatisfied를 부르므로 생성자 순환을 지연 조회로 끊는다.
    private final ObjectProvider<TicketService> ticketServiceProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final PayoutService payoutService;

    public Deposit createPending(Application application, RefundAccount returnAccount) {
        return depositRepository.save(Deposit.pending(application, returnAccount));
    }

    /**
     * 보증금 입금 확인. 신청이 결제 대기이고 이용권 사용(USE) 기록도 있으면 신청을 확정한다.
     * 이미 취소된 신청이면 확정하지 않고 바로 반환 대기로 둔다. 보증금이 없거나 이미 처리된 건이면 아무것도 하지 않는다(멱등).
     * 이번 호출이 입금 대기(PENDING) 보증금을 입금 확인했으면 true. 입금 대사는 false를 중복 입금으로 본다. (KAN-403)
     */
    public boolean markPaid(UUID applicationId) {
        // 잠근 뒤 DB 최신 상태로 판단한다(동시 입금).
        Optional<Deposit> pending = lockFresh(applicationId).filter(deposit -> deposit.getStatus() == DepositStatus.PENDING);
        pending.ifPresent(deposit -> {
            deposit.markPaid();
            Application application = deposit.getApplication();
            if (application.getStatus() == ApplicationStatus.CANCELLED) {
                startReturn(deposit, 100);
            } else if (application.getStatus() == ApplicationStatus.PAYMENT_PENDING
                    && ticketServiceProvider.getObject().hasTicketUse(applicationId)) {
                application.confirm();
                // @Async 확정 메일이 트랜잭션 밖에서 모임 제목(종류)을 읽으므로 여기서 로드해 둔다.
                Hibernate.initialize(application.getGathering());
                eventPublisher.publishEvent(new ApplicationConfirmedEvent(application));
            }
            eventPublisher.publishEvent(new ApplicationStatusChangedEvent(application, "보증금 입금 확인"));
        });
        return pending.isPresent();
    }

    /** 보증금이 없거나 입금 확인 이후 상태(PAID 이상)인가. */
    @Transactional(readOnly = true)
    public boolean isSatisfied(UUID applicationId) {
        return depositRepository.findByApplication_Id(applicationId).map(Deposit::isSatisfied).orElse(true);
    }

    /**
     * 반환 요청(PAID → RETURN_PENDING) + 신청 때 받은 반환 계좌로 운영자 송금 요청(DEPOSIT_RETURN) 1건.
     * 미입금·이미 처리된 보증금·보증금 없는 신청은 무시한다. 잠근 뒤 최신 상태로 판단해 송금 요청이 두 번 생기지 않는다. (KAN-404)
     */
    public void requestReturn(UUID applicationId) {
        requestReturn(applicationId, 100);
    }

    /** {@link #requestReturn(UUID)}과 같되 보증금의 percent%만 돌려준다(원 단위 버림, 나머지는 몰수). 마감 후 취소 50%. (KAN-418) */
    public void requestReturn(UUID applicationId, int percent) {
        // 0% 반환은 몰수다. 0원 송금 요청을 만들지 않는다.
        lockFresh(applicationId)
                .filter(deposit -> deposit.getStatus() == DepositStatus.PAID)
                .ifPresent(deposit -> {
                    if (percent > 0) {
                        startReturn(deposit, percent);
                    } else {
                        deposit.forfeit();
                    }
                });
    }

    /** 이 신청의 보증금 행을 잠그고 최신 상태로 읽는다(없으면 무시). 신청 취소가 신청보다 먼저 잡는다. 잠금 순서: 회차 → 테이블 → 보증금 → 신청. */
    public void lockForUpdate(UUID applicationId) {
        lockFresh(applicationId);
    }

    /** 몰수(PAID·RETURN_PENDING → FORFEITED). 반환 대기였으면 아직 송금 전인 반환 요청을 거둔다. 그 외는 무시한다. */
    public void forfeit(UUID applicationId) {
        lockFresh(applicationId).ifPresent(deposit -> {
            if (deposit.getStatus() == DepositStatus.RETURN_PENDING) {
                payoutService.withdrawDepositReturn(deposit.getId());
            }
            deposit.forfeit();
        });
    }

    /** 운영자 송금 완료 후속(PayoutSentEvent): RETURN_PENDING → RETURNED. */
    public void markReturned(UUID depositId) {
        depositRepository.findById(depositId).ifPresent(deposit -> {
            deposit.markReturned();
            eventPublisher.publishEvent(new ApplicationStatusChangedEvent(deposit.getApplication(), "보증금 반환 송금 완료"));
        });
    }

    /** 반환 송금 30일 후 보증금 쪽 계좌 사본도 끝 4자리만 남긴다. */
    public void maskReturnAccounts(Collection<UUID> depositIds) {
        if (!depositIds.isEmpty()) {
            depositRepository.findAllById(depositIds).forEach(Deposit::maskReturnAccount);
        }
    }

    // 대사 후보 조회 등으로 이미 올라온 사본이면 잠금 쿼리가 옛 상태를 돌려주므로 잠근 뒤 DB 최신 상태로 덮는다.
    private Optional<Deposit> lockFresh(UUID applicationId) {
        Optional<Deposit> locked = depositRepository.findByApplication_IdForUpdate(applicationId);
        locked.ifPresent(depositRepository::refreshForUpdate);
        return locked;
    }

    private void startReturn(Deposit deposit, int percent) {
        deposit.requestReturn(percent);
        Application application = deposit.getApplication();
        payoutService.request(PayoutType.DEPOSIT_RETURN, application.getUser().getId(), deposit.getReturnAmount(),
                deposit.getReturnAccount(), application.getId(), null, deposit.getId());
    }

    /** 입금 대사 후보: 취소되지 않은 신청의 입금 대기 보증금(50,000원). 이용권과 합산 입금 묶기는 대사 쪽이 한다. */
    @Transactional(readOnly = true)
    public List<PendingPayment> listPendingStandalone() {
        return depositRepository.findByStatusAndApplication_DeletedAtIsNullOrderByCreatedAtAsc(DepositStatus.PENDING).stream()
                .map(deposit -> {
                    Application application = deposit.getApplication();
                    String payerName = application.getUser() != null ? application.getUser().getName() : application.getName();
                    return new PendingPayment(PaymentTargetType.DEPOSIT, deposit.getId(), application.getId(),
                            payerName, deposit.getAmount());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Deposit> findPendingByApplication(UUID applicationId) {
        return depositRepository.findByApplication_Id(applicationId)
                .filter(deposit -> deposit.getStatus() == DepositStatus.PENDING);
    }

    /** 이 신청들 중 보증금 입금 전(PENDING)인 신청. 매칭 후보에서 뺀다. */
    @Transactional(readOnly = true)
    public Set<UUID> findUnsatisfiedApplicationIds(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Set.of();
        }
        return depositRepository.findByApplication_IdIn(applicationIds).stream()
                .filter(deposit -> !deposit.isSatisfied())
                .map(deposit -> deposit.getApplication().getId())
                .collect(Collectors.toSet());
    }

    /** 신청 ID → 보증금. 보증금이 없는 신청은 결과에 없다. */
    @Transactional(readOnly = true)
    public Map<UUID, Deposit> findByApplicationIds(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return depositRepository.findByApplication_IdIn(applicationIds).stream()
                .collect(Collectors.toMap(deposit -> deposit.getApplication().getId(), Function.identity()));
    }
}
