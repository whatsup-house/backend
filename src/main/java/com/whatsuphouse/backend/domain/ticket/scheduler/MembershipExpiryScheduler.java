package com.whatsuphouse.backend.domain.ticket.scheduler;

import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** 매일 00:10(KST): 만료일이 지난 멤버십을 만료 처리한다. (KAN-401) */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipExpiryScheduler {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final TicketService ticketService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void expireMemberships() {
        int expired = ticketService.expireMemberships(LocalDate.now(KOREA));
        if (expired > 0) {
            log.info("[Membership] 기간 만료 처리: {}건", expired);
        }
    }
}
