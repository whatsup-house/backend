package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 동시 입금 시나리오(KAN-403): 대사 후보 조회로 대상이 이미 영속성 컨텍스트에 올라온 뒤 다른 트랜잭션이 먼저 정산하면,
 * 잠금 쿼리가 옛 사본을 돌려줘도 정산 가드는 DB 최신 상태로 판단해야 한다(중복 정산 금지). 실제 H2 트랜잭션으로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SettleLockRefreshTest {

    @Autowired private DepositService depositService;
    @Autowired private ApplicationService applicationService;
    @Autowired private TicketService ticketService;
    @Autowired private UserRepository userRepository;
    @Autowired private GatheringRepository gatheringRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private DepositRepository depositRepository;
    @Autowired private TicketProductRepository ticketProductRepository;
    @Autowired private TicketPassRepository ticketPassRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private Application application;
    private TicketPass pass;

    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder().email(key + "@example.com").password("encoded").name("한지형")
                .gender(Gender.MALE).age(30).nickname("n" + key).build());
        Gathering randomTable = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        Application pending = Application.builder().bookingNumber("WH261020-LOCK01").gathering(randomTable)
                .user(user).name("한지형").phone("01012345678").build();
        pending.awaitPayment();
        application = applicationRepository.save(pending);
        depositRepository.save(Deposit.pending(application, null));
        TicketProductOption single = ticketProductRepository.save(new TicketProductOption("1회권", 1, 9_900));
        pass = ticketPassRepository.save(new TicketPass(user, application, single));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // 바깥 트랜잭션에서 대상을 먼저 읽어 두고(stale 사본), 다른 트랜잭션이 먼저 정산한 것처럼 커밋한 뒤 가드를 부른다.
    private void inStaleTransaction(Runnable preload, String concurrentUpdate, Object[] args, Runnable guard) {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            preload.run();
            other.executeWithoutResult(s -> jdbcTemplate.update(concurrentUpdate, args));
            guard.run();
            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("보증금: 잠그기 전에 읽어 둔 사본이 PENDING이어도 이미 PAID로 커밋됐으면 markPaid는 false")
    void markPaid_staleManagedCopy_returnsFalse() {
        boolean[] marked = new boolean[1];
        inStaleTransaction(
                () -> assertThat(depositRepository.findByApplication_Id(application.getId()).orElseThrow().getStatus())
                        .isEqualTo(DepositStatus.PENDING),
                "UPDATE deposits SET status = 'PAID' WHERE application_id = ?", new Object[]{application.getId()},
                () -> marked[0] = depositService.markPaid(application.getId()));

        assertThat(marked[0]).isFalse();
    }

    @Test
    @DisplayName("일반 모임: 읽어 둔 사본이 미확인이어도 이미 입금 확인이 커밋됐으면 confirmRegularPaymentBySystem은 false")
    void confirmRegularPayment_staleManagedCopy_returnsFalse() {
        boolean[] confirmed = new boolean[1];
        inStaleTransaction(
                () -> assertThat(applicationRepository.findById(application.getId()).orElseThrow().isPaymentConfirmed())
                        .isFalse(),
                "UPDATE applications SET payment_confirmed_at = ? WHERE id = ?",
                new Object[]{LocalDateTime.now(), application.getId()},
                () -> confirmed[0] = applicationService.confirmRegularPaymentBySystem(application.getId()));

        assertThat(confirmed[0]).isFalse();
    }

    @Test
    @DisplayName("이용권: 읽어 둔 사본이 PENDING이어도 이미 ACTIVE로 커밋됐으면 settlePass는 TICKET_ALREADY_PROCESSED")
    void settlePass_staleManagedCopy_throws() {
        inStaleTransaction(
                () -> assertThat(ticketPassRepository.findById(pass.getId()).orElseThrow().getStatus())
                        .isEqualTo(TicketPassStatus.PENDING),
                "UPDATE ticket_passes SET status = 'ACTIVE' WHERE id = ?", new Object[]{pass.getId()},
                () -> assertThatThrownBy(() -> ticketService.settlePass(pass.getId(), "WEBHOOK"))
                        .isInstanceOf(CustomException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_ALREADY_PROCESSED));
    }
}
