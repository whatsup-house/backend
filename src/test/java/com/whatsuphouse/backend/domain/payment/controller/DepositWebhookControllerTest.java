package com.whatsuphouse.backend.domain.payment.controller;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.repository.DepositNotificationRepository;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 웹훅·심사용 시뮬레이터를 실제 보안 설정·트랜잭션·DB(H2)로 확인한다. 끝나면 공유 H2를 비운다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.deposit-webhook.secret=test-webhook-secret")
class DepositWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String ALERT = "[입금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30";

    @Autowired private MockMvc mockMvc;
    @Autowired private DepositNotificationRepository depositNotificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GatheringRepository gatheringRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private TicketProductRepository ticketProductRepository;
    @Autowired private TicketPassRepository ticketPassRepository;
    @Autowired private DepositRepository depositRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private TicketService ticketService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private static String body(String text) {
        return "{\"text\":\"" + text + "\",\"source\":\"MACRODROID\"}";
    }

    @Test
    @DisplayName("시크릿이 틀리거나 없으면 401, 알림은 저장하지 않는다")
    void webhook_wrongSecret_401() throws Exception {
        // when & then
        mockMvc.perform(post("/api/payments/deposit-webhook").header("X-Deposit-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body(ALERT)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/deposit-webhook")
                        .contentType(MediaType.APPLICATION_JSON).content(body(ALERT)))
                .andExpect(status().isUnauthorized());
        assertThat(depositNotificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("본문이 2KB를 넘으면 413")
    void webhook_tooLarge_413() throws Exception {
        // when & then
        mockMvc.perform(post("/api/payments/deposit-webhook").header("X-Deposit-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(body("가".repeat(1_000))))
                .andExpect(status().isPayloadTooLarge());
        assertThat(depositNotificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("text가 없으면 400")
    void webhook_blankText_400() throws Exception {
        mockMvc.perform(post("/api/payments/deposit-webhook").header("X-Deposit-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("MacroDroid 재시도로 같은 원문이 두 번 오면 한 번만 처리하고 두 번째도 200")
    void webhook_duplicateText_processedOnce() throws Exception {
        // when
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/payments/deposit-webhook").header("X-Deposit-Secret", SECRET)
                            .contentType(MediaType.APPLICATION_JSON).content(body(ALERT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result").value("NO_CANDIDATE"))
                    .andExpect(jsonPath("$.data.parsedName").value("한지형"))
                    .andExpect(jsonPath("$.data.parsedAmount").value(40_000));
        }

        // then
        assertThat(depositNotificationRepository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exception_cases", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("데모 회원이 아니면 시뮬레이터 403")
    void demoDeposit_nonDemoUser_403() throws Exception {
        // given
        User member = saveUser("일반회원", false);

        // when & then
        mockMvc.perform(post("/api/payments/demo-deposit").with(user(new UserPrincipal(member.getId(), member.getEmail(), false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applicationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(depositNotificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("passId와 applicationId는 정확히 하나만")
    void demoDeposit_bothOrNeither_400() throws Exception {
        User demo = saveUser("체험회원", true);
        UserPrincipal principal = new UserPrincipal(demo.getId(), demo.getEmail(), false);

        mockMvc.perform(post("/api/payments/demo-deposit").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payments/demo-deposit").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passId\":\"" + UUID.randomUUID() + "\",\"applicationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("데모 회원 1회권+보증금 59,900 시뮬레이터 입금 → 1건 매칭, 보증금 PAID·1회권 발급 후 이 신청에 사용(USED_UP)·신청 CONFIRMED")
    void demoDeposit_bundledPassAndDeposit_confirmsApplication() throws Exception {
        // given
        User demo = saveUser("체험김와썹", true);
        Gathering randomTable = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        Application application = Application.builder().bookingNumber("WH261020-DEMO01").gathering(randomTable)
                .user(demo).name("체험김와썹").phone("01012345678").build();
        application.awaitPayment();
        application = applicationRepository.save(application);
        TicketProductOption single = ticketProductRepository.save(new TicketProductOption("1회권", 1, 9_900));
        TicketPass pass = ticketPassRepository.save(new TicketPass(demo, application, single));
        depositRepository.save(Deposit.pending(application, null));

        // when
        mockMvc.perform(post("/api/payments/demo-deposit").with(user(new UserPrincipal(demo.getId(), demo.getEmail(), false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passId\":\"" + pass.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("MATCHED"))
                .andExpect(jsonPath("$.data.parsedName").value("체험김와썹"))
                .andExpect(jsonPath("$.data.parsedAmount").value(59_900))
                .andExpect(jsonPath("$.data.linkedApplicationId").value(application.getId().toString()));

        // then
        assertThat(depositRepository.findByApplication_Id(application.getId()).orElseThrow().getStatus())
                .isEqualTo(DepositStatus.PAID);
        assertThat(ticketPassRepository.findById(pass.getId()).orElseThrow().getStatus()).isEqualTo(TicketPassStatus.USED_UP);
        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("정산이 실패해도(경쟁 등) 보증금 입금 처리는 롤백되고 알림·예외 건은 남으며 200")
    void webhook_settleFails_keepsNotificationAndRollsBackSettlement() throws Exception {
        // given
        User member = saveUser("한지형", false);
        Gathering randomTable = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        Application application = Application.builder().bookingNumber("WH261020-RACE01").gathering(randomTable)
                .user(member).name("한지형").phone("01012345678").build();
        application.awaitPayment();
        application = applicationRepository.save(application);
        TicketProductOption single = ticketProductRepository.save(new TicketProductOption("1회권", 1, 9_900));
        TicketPass pass = ticketPassRepository.save(new TicketPass(member, application, single));
        depositRepository.save(Deposit.pending(application, null));
        willThrow(new CustomException(ErrorCode.TICKET_ALREADY_PROCESSED)).given(ticketService).settlePass(any(), anyString());
        String alert = "[입금] 한지형        59,900원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:31";

        // when
        mockMvc.perform(post("/api/payments/deposit-webhook").header("X-Deposit-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(body(alert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("NO_CANDIDATE"));

        // then
        assertThat(depositNotificationRepository.count()).isEqualTo(1);
        assertThat(depositNotificationRepository.findAll().get(0).getExceptionCaseId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM exception_cases WHERE application_id = ?",
                Long.class, application.getId())).isEqualTo(1);
        assertThat(depositRepository.findByApplication_Id(application.getId()).orElseThrow().getStatus())
                .isEqualTo(DepositStatus.PENDING);
        assertThat(ticketPassRepository.findById(pass.getId()).orElseThrow().getStatus()).isEqualTo(TicketPassStatus.PENDING);
    }

    private User saveUser(String name, boolean demo) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().email(key + "@example.com").password("encoded").name(name)
                .gender(Gender.MALE).age(30).nickname("n" + key).build();
        if (demo) {
            user.markDemo();
        }
        return userRepository.save(user);
    }
}
