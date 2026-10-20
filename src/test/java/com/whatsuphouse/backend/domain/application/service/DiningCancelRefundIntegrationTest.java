package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse.DepositOutcome;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.CancelWindow;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.service.MatchingEngine;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.entity.PayoutRequest;
import com.whatsuphouse.backend.domain.payment.entity.RefundAccount;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.payment.repository.PayoutRequestRepository;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.entity.TicketTransaction;
import com.whatsuphouse.backend.domain.ticket.enums.TicketDeductionStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import com.whatsuphouse.backend.domain.ticket.enums.TicketTransactionType;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketTransactionRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 취소 환불 정책(KAN-418) 통합 테스트: 시점(마감 전·후·당일 앉음/안 앉음) × 이용권(1회권·멤버십·무료) × 보증금(없음·입금)마다
 * 미리보기 금액과 실제 송금 요청(금액·계좌·상태), 차감 기록·보증금 상태가 같은지 실제 DB(H2)로 확인한다. 끝나면 공유 H2를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "dining.system-admin-id=00000000-0000-0000-0000-00000000a001")
class DiningCancelRefundIntegrationTest {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    // 홀수 금액이라 50%가 원 단위 버림(7,500)되는지도 본다.
    private static final int COUNT_PRICE = 15_001;
    private static final RefundAccount REQUEST_ACCOUNT = new RefundAccount("신한", "110123456789", "김철수");
    private static final RefundAccount DEPOSIT_ACCOUNT = new RefundAccount("국민", "12345678901", "김철수");

    enum When {
        BEFORE_DEADLINE(3, 100), AFTER_DEADLINE(1, 50), SAME_DAY_UNSEATED(0, 50), SAME_DAY_SEATED(0, 0);

        final int days;
        final int percent;

        When(int days, int percent) {
            this.days = days;
            this.percent = percent;
        }
    }

    enum Pass { COUNT, MEMBERSHIP, FREE }

    // H2에서는 application_answers.value가 예약어라 답변 조회가 실패한다(테이블 재조정). 답변 없음으로 둔다.
    @MockitoSpyBean
    private AdminApplicationService adminApplicationService;

    @Autowired private MockMvc mockMvc;
    @Autowired private DiningApplicationService diningApplicationService;
    @Autowired private TicketService ticketService;
    @Autowired private DepositService depositService;
    @Autowired private UserRepository userRepository;
    @Autowired private GatheringRepository gatheringRepository;
    @Autowired private GatheringSessionRepository gatheringSessionRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private TicketProductRepository ticketProductRepository;
    @Autowired private TicketPassRepository ticketPassRepository;
    @Autowired private TicketTransactionRepository ticketTransactionRepository;
    @Autowired private DepositRepository depositRepository;
    @Autowired private PayoutRequestRepository payoutRequestRepository;
    @Autowired private DiningTableRepository diningTableRepository;
    @Autowired private DiningTableMemberRepository diningTableMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Gathering gathering;

    @BeforeEach
    void setUp() {
        willReturn(List.of()).given(adminApplicationService).findAnswers(any());
        gathering = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    static Stream<Arguments> cases() {
        return Arrays.stream(When.values()).flatMap(when -> Arrays.stream(Pass.values()).flatMap(pass ->
                Stream.of(Arguments.of(when, pass, false), Arguments.of(when, pass, true))));
    }

    @ParameterizedTest(name = "{0} / {1} / 보증금 입금={2}")
    @MethodSource("cases")
    @DisplayName("시점 × 이용권 × 보증금: 미리보기 금액 = 실제 송금 요청 금액, 차감·보증금 상태")
    void cancel_matchesPreviewAndPolicy(When when, Pass passType, boolean depositPaid) {
        // given
        Fixture f = fixture(when, passType, depositPaid, false);
        int expectedTicket = passType == Pass.COUNT ? COUNT_PRICE * when.percent / 100 : 0;
        int expectedDeposit = depositPaid ? Deposit.AMOUNT * when.percent / 100 : 0;
        boolean seated = when == When.SAME_DAY_SEATED;

        // when
        DiningCancelPreviewResponse preview = diningApplicationService.previewCancel(f.application.getId(), f.user.getId());
        diningApplicationService.cancelApplication(f.application.getId(), f.user.getId(),
                expectedTicket > 0 ? REQUEST_ACCOUNT : null);

        // then: 미리보기
        DepositOutcome expectedOutcome = !depositPaid ? DepositOutcome.NONE
                : when.percent == 100 ? DepositOutcome.RETURN
                : when.percent == 50 ? DepositOutcome.PARTIAL : DepositOutcome.FORFEIT;
        assertThat(preview).isEqualTo(new DiningCancelPreviewResponse(
                when == When.BEFORE_DEADLINE ? CancelWindow.BEFORE_DEADLINE
                        : when == When.AFTER_DEADLINE ? CancelWindow.AFTER_DEADLINE : CancelWindow.SAME_DAY,
                false, expectedTicket > 0, expectedOutcome, seated,
                expectedTicket, expectedDeposit, expectedTicket > 0));

        // then: 실제 송금 요청 = 미리보기 금액
        assertThat(applicationRepository.findIncludingDeletedById(f.application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CANCELLED);
        List<PayoutRequest> payouts = payouts(f.application.getId());
        assertThat(payouts).hasSize((expectedTicket > 0 ? 1 : 0) + (expectedDeposit > 0 ? 1 : 0));
        assertThat(payouts).allMatch(p -> p.getStatus() == PayoutStatus.REQUESTED);
        Optional<PayoutRequest> refund = payout(payouts, PayoutType.REFUND);
        assertThat(refund.map(PayoutRequest::getAmount).orElse(0)).isEqualTo(preview.ticketRefundAmount());
        refund.ifPresent(p -> {
            assertThat(p.getAccount().getBank()).isEqualTo("신한");
            assertThat(p.getTicketPassId()).isEqualTo(f.pass.getId());
        });
        Optional<PayoutRequest> depositReturn = payout(payouts, PayoutType.DEPOSIT_RETURN);
        assertThat(depositReturn.map(PayoutRequest::getAmount).orElse(0)).isEqualTo(preview.depositReturnAmount());
        depositReturn.ifPresent(p -> assertThat(p.getAccount().getBank()).isEqualTo("국민"));

        // then: 이용권은 복원하지 않는다. 1회권 환불이면 송금 대기, 그 외 차감은 그대로.
        if (passType != Pass.FREE) {
            assertThat(lastUse(f.application.getId()).getStatus()).isEqualTo(
                    expectedTicket > 0 ? TicketDeductionStatus.REFUND_PROCESSING : TicketDeductionStatus.DEDUCTED);
            assertThat(ticketPassRepository.findById(f.pass.getId()).orElseThrow().getRemainingCount()).isZero();
            assertThat(ticketTransactionRepository.findByTicketPass_IdOrderByCreatedAtDesc(f.pass.getId()))
                    .noneMatch(t -> t.getTransactionType() == TicketTransactionType.REFUND);
        }

        // then: 보증금·제재
        if (depositPaid) {
            Deposit deposit = depositRepository.findByApplication_Id(f.application.getId()).orElseThrow();
            assertThat(deposit.getStatus()).isEqualTo(seated ? DepositStatus.FORFEITED : DepositStatus.RETURN_PENDING);
            assertThat(deposit.getReturnAmount()).isEqualTo(seated ? null : expectedDeposit);
        }
        assertThat(userRepository.findById(f.user.getId()).orElseThrow().isDepositRequired()).isEqualTo(seated);
    }

    @Test
    @DisplayName("이용권 환불이 있는데 계좌가 없으면 REFUND_ACCOUNT_REQUIRED, 신청·차감·보증금·송금 모두 그대로")
    void cancel_refundDue_withoutAccount_rejected() {
        // given
        Fixture f = fixture(When.AFTER_DEADLINE, Pass.COUNT, true, false);

        // when & then
        assertThatThrownBy(() -> diningApplicationService.cancelApplication(f.application.getId(), f.user.getId(), null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_ACCOUNT_REQUIRED);
        assertThat(applicationRepository.findById(f.application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CONFIRMED);
        assertThat(lastUse(f.application.getId()).getStatus()).isEqualTo(TicketDeductionStatus.DEDUCTED);
        assertThat(depositRepository.findByApplication_Id(f.application.getId()).orElseThrow().getStatus())
                .isEqualTo(DepositStatus.PAID);
        assertThat(payouts(f.application.getId())).isEmpty();
    }

    @Test
    @DisplayName("데모 회원(마감 후, 1회권, 보증금 입금): 송금 요청이 바로 SENT, 차감 REFUNDED, 보증금 RETURNED(25,000원)")
    void cancel_demoUser_autoSent() {
        // given
        Fixture f = fixture(When.AFTER_DEADLINE, Pass.COUNT, true, true);

        // when
        diningApplicationService.cancelApplication(f.application.getId(), f.user.getId(), REQUEST_ACCOUNT);

        // then
        List<PayoutRequest> payouts = payouts(f.application.getId());
        assertThat(payouts).extracting(PayoutRequest::getType, PayoutRequest::getAmount, PayoutRequest::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(PayoutType.REFUND, 7_500, PayoutStatus.SENT),
                        org.assertj.core.groups.Tuple.tuple(PayoutType.DEPOSIT_RETURN, 25_000, PayoutStatus.SENT));
        assertThat(lastUse(f.application.getId()).getStatus()).isEqualTo(TicketDeductionStatus.REFUNDED);
        Deposit deposit = depositRepository.findByApplication_Id(f.application.getId()).orElseThrow();
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.RETURNED);
        assertThat(deposit.getReturnAmount()).isEqualTo(25_000);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessionCancelledWindows")
    @DisplayName("운영자가 회차를 취소한 뒤 취소: 이용권은 이미 복원돼 환불 0, 보증금 50,000원 전액 반환, 제재 없음")
    void cancel_afterSessionCancelled_fullDepositReturn(When when) {
        // given: 회차 취소 일괄 처리처럼 회차를 CANCELLED로 바꾸고 차감을 복원한다.
        Fixture f = fixture(when, Pass.COUNT, true, false);
        GatheringSession session = gatheringSessionRepository.findById(f.application.getSession().getId()).orElseThrow();
        session.changeStatus(GatheringSessionStatus.CANCELLED);
        gatheringSessionRepository.save(session);
        ticketService.refundOneTicket(f.application);

        // when
        DiningCancelPreviewResponse preview = diningApplicationService.previewCancel(f.application.getId(), f.user.getId());
        diningApplicationService.cancelApplication(f.application.getId(), f.user.getId(), null);

        // then
        assertThat(preview.depositOutcome()).isEqualTo(DepositOutcome.RETURN);
        assertThat(preview.depositReturnAmount()).isEqualTo(50_000);
        assertThat(preview.ticketRefundAmount()).isZero();
        assertThat(preview.requiresRefundAccount()).isFalse();
        assertThat(preview.sanction()).isFalse();
        assertThat(payouts(f.application.getId())).extracting(PayoutRequest::getType, PayoutRequest::getAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(PayoutType.DEPOSIT_RETURN, 50_000));
        Deposit deposit = depositRepository.findByApplication_Id(f.application.getId()).orElseThrow();
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.RETURN_PENDING);
        assertThat(deposit.getReturnAmount()).isEqualTo(50_000);
        assertThat(userRepository.findById(f.user.getId()).orElseThrow().isDepositRequired()).isFalse();
    }

    static Stream<When> sessionCancelledWindows() {
        return Stream.of(When.AFTER_DEADLINE, When.SAME_DAY_SEATED);
    }

    @Test
    @DisplayName("동시 취소: 신청 행을 잠가 하나만 취소되고 다른 요청은 APPLICATION_ALREADY_CANCELLED, 송금 요청은 한 벌만")
    void cancel_concurrent_secondGets409() throws Exception {
        // given
        Fixture f = fixture(When.BEFORE_DEADLINE, Pass.COUNT, true, false);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Object> cancel = () -> {
            start.await();
            try {
                diningApplicationService.cancelApplication(f.application.getId(), f.user.getId(), REQUEST_ACCOUNT);
                return "OK";
            } catch (CustomException e) {
                return e.getErrorCode();
            }
        };

        // when
        java.util.concurrent.Future<Object> first = pool.submit(cancel);
        java.util.concurrent.Future<Object> second = pool.submit(cancel);
        start.countDown();
        List<Object> results = List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();

        // then
        assertThat(results).containsExactlyInAnyOrder("OK", ErrorCode.APPLICATION_ALREADY_CANCELLED);
        assertThat(payouts(f.application.getId())).extracting(PayoutRequest::getType, PayoutRequest::getAmount)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(PayoutType.REFUND, COUNT_PRICE),
                        org.assertj.core.groups.Tuple.tuple(PayoutType.DEPOSIT_RETURN, 50_000));
    }

    @Test
    @DisplayName("HTTP: 미리보기 필드 이름, 본문 없이 취소하면 REFUND_ACCOUNT_REQUIRED, 계좌 길이 초과는 400, 계좌를 주면 취소")
    void http_previewAndCancelContract() throws Exception {
        // given
        Fixture f = fixture(When.AFTER_DEADLINE, Pass.COUNT, true, false);
        UserPrincipal principal = new UserPrincipal(f.user.getId(), f.user.getEmail(), false);
        String base = "/api/dining/applications/" + f.application.getId();

        // when & then
        mockMvc.perform(get(base + "/cancel-preview").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window").value("AFTER_DEADLINE"))
                .andExpect(jsonPath("$.data.ticketRestored").value(false))
                .andExpect(jsonPath("$.data.refundable").value(true))
                .andExpect(jsonPath("$.data.depositOutcome").value("PARTIAL"))
                .andExpect(jsonPath("$.data.sanction").value(false))
                .andExpect(jsonPath("$.data.ticketRefundAmount").value(7_500))
                .andExpect(jsonPath("$.data.depositReturnAmount").value(25_000))
                .andExpect(jsonPath("$.data.requiresRefundAccount").value(true));
        mockMvc.perform(post(base + "/cancel").with(user(principal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_REQUIRED"));
        mockMvc.perform(post(base + "/cancel").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAccount\":{\"bank\":\"신한\",\"accountNumber\":\"" + "1".repeat(51)
                                + "\",\"holder\":\"김철수\"}}"))
                .andExpect(status().isBadRequest());
        assertThat(payouts(f.application.getId())).isEmpty();
        mockMvc.perform(post(base + "/cancel").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAccount\":{\"bank\":\"신한\",\"accountNumber\":\"110123456789\",\"holder\":\"김철수\"}}"))
                .andExpect(status().isOk());
        assertThat(payouts(f.application.getId())).extracting(PayoutRequest::getType, PayoutRequest::getAmount)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(PayoutType.REFUND, 7_500),
                        org.assertj.core.groups.Tuple.tuple(PayoutType.DEPOSIT_RETURN, 25_000));
    }

    @Test
    @DisplayName("HTTP: 환불할 이용권이 없으면(무료) 본문 없이 취소된다")
    void http_cancelWithoutBody_whenNothingToRefund() throws Exception {
        // given
        Fixture f = fixture(When.BEFORE_DEADLINE, Pass.FREE, false, false);

        // when & then
        mockMvc.perform(post("/api/dining/applications/" + f.application.getId() + "/cancel")
                        .with(user(new UserPrincipal(f.user.getId(), f.user.getEmail(), false))))
                .andExpect(status().isOk());
        assertThat(applicationRepository.findIncludingDeletedById(f.application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CANCELLED);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private record Fixture(User user, Application application, TicketPass pass) {
    }

    private Fixture fixture(When when, Pass passType, boolean depositPaid, boolean demo) {
        User user = newUser(demo);
        // 당일 회차는 아직 시작 전이어야 취소할 수 있다.
        GatheringSession session = gatheringSessionRepository.save(GatheringSession.builder().gathering(gathering)
                .eventDate(LocalDate.now(KOREA).plusDays(when.days))
                .startTime(when.days == 0 ? LocalTime.of(23, 59) : LocalTime.of(19, 0))
                .maxAttendees(12).build());
        Application application = Application.builder().bookingNumber("WH-" + UUID.randomUUID().toString().substring(0, 8))
                .gathering(gathering).session(session).user(user).name(user.getName()).phone("01012345678").build();
        application.confirm();
        application = applicationRepository.save(application);

        TicketPass pass = null;
        if (passType != Pass.FREE) {
            TicketProductOption product = ticketProductRepository.save(passType == Pass.COUNT
                    ? new TicketProductOption("1회권", 1, COUNT_PRICE)
                    : new TicketProductOption("멤버십", 0, 99_000, TicketProductKind.PERIOD, 1));
            pass = new TicketPass(user, null, product);
            pass.activate();
            pass = ticketPassRepository.save(pass);
            ticketTransactionRepository.save(TicketTransaction.of(
                    pass, null, TicketTransactionType.ISSUE, pass.getTotalCount(), "테스트 발급"));
            assertThat(ticketService.tryUseOneTicket(user, application, session.getEventDate())).isTrue();
        }
        if (depositPaid) {
            depositService.createPending(application, DEPOSIT_ACCOUNT);
            depositService.markPaid(application.getId());
        }
        if (when == When.SAME_DAY_SEATED) {
            DiningTable table = diningTableRepository.save(DiningTable.builder().session(session)
                    .eventDate(session.getEventDate()).groupSize(4).algorithmVersion(MatchingEngine.ALGORITHM_VERSION).build());
            diningTableMemberRepository.save(DiningTableMember.builder().application(application)
                    .table(table).seatOrder(1).assignReason(AssignReason.INITIAL).build());
        }
        return new Fixture(user, application, pass);
    }

    private User newUser(boolean demo) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().email(key + "@example.com").password("encoded").name("회원" + key)
                .gender(Gender.MALE).age(30).nickname("회원" + key).build();
        user.approveRandomTable();
        if (demo) {
            user.markDemo();
        }
        return userRepository.save(user);
    }

    private List<PayoutRequest> payouts(UUID applicationId) {
        return payoutRequestRepository.findAll().stream()
                .filter(p -> applicationId.equals(p.getApplicationId()))
                .toList();
    }

    private static Optional<PayoutRequest> payout(List<PayoutRequest> payouts, PayoutType type) {
        return payouts.stream().filter(p -> p.getType() == type).findFirst();
    }

    private TicketTransaction lastUse(UUID applicationId) {
        return ticketTransactionRepository
                .findFirstByApplication_IdAndTransactionTypeOrderByCreatedAtDesc(applicationId, TicketTransactionType.USE)
                .orElseThrow();
    }
}
