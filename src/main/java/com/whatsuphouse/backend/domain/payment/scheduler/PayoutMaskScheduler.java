package com.whatsuphouse.backend.domain.payment.scheduler;

import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.payment.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 매일 03:30(KST): 송금 30일이 지난 환불·보증금 반환 계좌번호를 끝 4자리만 남기고 가린다. (설계 3.3, KAN-404) */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutMaskScheduler {

    static final int RETENTION_DAYS = 30;

    private final PayoutService payoutService;
    private final DepositService depositService;

    // 송금 건과 보증금 사본을 한 트랜잭션으로 가린다(한쪽만 가려진 채 남지 않게).
    @Transactional
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void maskSentAccounts() {
        List<UUID> depositIds = payoutService.maskAccountsSentBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        depositService.maskReturnAccounts(depositIds);
        if (!depositIds.isEmpty()) {
            log.info("[Payout] 송금 30일 경과 보증금 반환 계좌 마스킹: {}건", depositIds.size());
        }
    }
}
