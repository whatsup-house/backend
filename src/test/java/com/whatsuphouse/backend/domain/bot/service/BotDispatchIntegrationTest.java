package com.whatsuphouse.backend.domain.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.bot.entity.BotDispatch;
import com.whatsuphouse.backend.domain.bot.enums.BotDispatchStatus;
import com.whatsuphouse.backend.domain.bot.repository.BotDispatchRepository;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageSendRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.ticket.admin.service.AdminTicketService;
import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.entity.TicketProductOption;
import com.whatsuphouse.backend.domain.ticket.repository.TicketPassRepository;
import com.whatsuphouse.backend.domain.ticket.repository.TicketProductRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.auth.JwtTokenProvider;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 그록봇 자동응답(KAN-406) 통합 테스트: 고객 메시지 → 디바운스 큐(PENDING) → Slack 신호(WireMock)
 * → 에이전트가 GET /api/bot/pending으로 lease(LEASED) → POST /api/bot/replies.
 * 합성 고객 A(신청 입금확인 완료·이용권 잔여 3회)와 B(신청 입금 대기·이용권 없음)만 쓴다. 외부 네트워크는 호출하지 않는다.
 * 스케줄러는 목으로 막고 signalIfDue/expireLeases/handOffUnclaimed에 시각을 넘긴다.
 * 디바운스(10초)를 기다리지 않도록 send_after·leased_until은 JDBC로 당긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bot.enabled=true",
        "bot.reply-token=test-reply-token"
})
class BotDispatchIntegrationTest {

    private static final String SLACK_PATH = "/services/T000/B000/signal";
    private static final String REPLY_TOKEN = "test-reply-token";

    @RegisterExtension
    static WireMockExtension slack = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void slackUrl(DynamicPropertyRegistry registry) {
        registry.add("bot.slack-signal-webhook-url", () -> slack.baseUrl() + SLACK_PATH);
    }

    // 백그라운드 폴링이 테스트가 넘기는 시각과 겹치지 않게 막는다.
    @MockitoBean
    private BotDispatchScheduler botDispatchScheduler;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ChatService chatService;
    @Autowired
    private AdminChatService adminChatService;
    @Autowired
    private BotDispatchService botDispatchService;
    @Autowired
    private BotSignalService botSignalService;
    @Autowired
    private BotDispatchRepository botDispatchRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ExceptionCaseRepository exceptionCaseRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GatheringRepository gatheringRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private TicketProductRepository ticketProductRepository;
    @Autowired
    private TicketPassRepository ticketPassRepository;
    @Autowired
    private AdminTicketService adminTicketService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Gathering gathering;
    private User admin;

    @BeforeEach
    void setUp() {
        slack.stubFor(post(urlEqualTo(SLACK_PATH)).willReturn(ok("ok")));
        ReflectionTestUtils.setField(botSignalService, "retryBackoff", List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO));
        gathering = gatheringRepository.save(
                Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build());
        admin = user("운영자", false);
        ReflectionTestUtils.setField(admin, "isAdmin", true);
        admin = userRepository.save(admin);
    }

    // 공유 H2(DB_CLOSE_DELAY=-1)에 커밋된 행이 남으면 다른 컨텍스트의 테스트가 깨진다.
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(botDispatchService, "allowedUserIds", Set.of());
        ReflectionTestUtils.setField(botSignalService, "signaledUpTo", BotSignalService.NEVER);
        ReflectionTestUtils.setField(botSignalService, "nextSignalAt", BotSignalService.NEVER);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ── 대기 문의 조회(lease) ──

    @Test
    @DisplayName("A·B를 한 번에 조회해도 각 항목 context엔 그 고객 본인 데이터만 있고 상대의 id·이름·신청 id·전화번호는 없다")
    void pending_customersAandB_eachContextOnlyOwnData() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        Application aApplication = application(a);
        aApplication.confirm();
        aApplication.confirmPayment();
        applicationRepository.save(aApplication);
        activePass(a, 3);
        User b = demoCustomer("고객B", "박비");
        Application bApplication = application(b);
        bApplication.awaitPayment();
        applicationRepository.save(bApplication);

        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        say(aRoom, a, "제 신청 입금 확인됐나요?");
        say(bRoom, b, "입금했는데 확인 부탁드려요");
        awaitDispatch(aRoom);
        awaitDispatch(bRoom);
        makeDue(aRoom);
        makeDue(bRoom);

        // when
        JsonNode items = pending(null);

        // then
        assertThat(items).hasSize(2);
        JsonNode aJson = itemOf(items, a.getId());
        JsonNode bJson = itemOf(items, b.getId());
        String aBody = aJson.toString();
        String bBody = bJson.toString();
        assertThat(aBody).doesNotContain(b.getId().toString(), "고객B", "박비", bApplication.getId().toString(), bRoom.toString());
        assertThat(aBody).doesNotContain(a.getEmail(), "\"email\"");
        // 전화번호: B의 번호·뒤 4자리가 A 항목의 어떤 값에도 없다(UUID·시각 숫자와 우연히 겹치지 않게 값 단위로 본다).
        assertThat(b.getPhone()).isNotEqualTo(a.getPhone());
        String bLast4 = b.getPhone().substring(b.getPhone().length() - 4);
        assertThat(aBody).doesNotContain(b.getPhone());
        assertThat(textValues(aJson))
                .noneMatch(v -> v.contains(b.getPhone()) || v.equals(bLast4) || v.endsWith("*" + bLast4));
        assertThat(bBody).doesNotContain(a.getId().toString(), "고객A", "김에이", aApplication.getId().toString(), aRoom.toString());

        List<String> fields = new ArrayList<>();
        aJson.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("eventId", "conversationId", "messageId", "customer", "message", "history",
                "context", "faq");
        BotDispatch aDispatch = dispatchOf(aRoom);
        assertThat(aJson.path("eventId").asText()).isEqualTo(aDispatch.getEventId().toString());
        assertThat(aJson.path("conversationId").asText()).isEqualTo(aRoom.toString());
        assertThat(aJson.path("messageId").asText()).isEqualTo(aDispatch.getMessageId().toString());
        assertThat(aJson.path("customer").path("displayName").asText()).isEqualTo("고객A");
        assertThat(aJson.path("message").path("text").asText()).isEqualTo("제 신청 입금 확인됐나요?");
        assertThat(aJson.path("message").path("createdAt").asText()).contains("T");
        assertThat(aJson.path("history").get(0).path("role").asText()).isEqualTo("customer");
        assertThat(aJson.path("context").path("applications").get(0).path("id").asText())
                .isEqualTo(aApplication.getId().toString());
        assertThat(aJson.path("context").path("applications").get(0).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(aJson.path("context").path("tickets").path("totalRemaining").asInt()).isEqualTo(3);
        assertThat(aJson.path("faq").size()).isPositive();
        assertThat(aJson.path("faq").get(0).path("q").asText()).isNotBlank();

        assertThat(bJson.path("context").path("applications").get(0).path("status").asText()).isEqualTo("PAYMENT_PENDING");
        assertThat(bJson.path("context").path("tickets").path("totalRemaining").asInt()).isZero();
        assertThat(aDispatch.getStatus()).isEqualTo(BotDispatchStatus.LEASED);
        assertThat(aDispatch.getAttempts()).isEqualTo(1);
        assertThat(aDispatch.getLeasedUntil()).isBetween(LocalDateTime.now().plusMinutes(4), LocalDateTime.now().plusMinutes(6));
        assertThat(dispatchOf(bRoom).getStatus()).isEqualTo(BotDispatchStatus.LEASED);
        assertThat(pending(null)).isEmpty();
    }

    @Test
    @DisplayName("두 호출이 동시에 lease해도 같은 문의가 두 번 반환되지 않는다")
    void pending_concurrentTwoCalls_noItemReturnedTwice() throws Exception {
        // given
        int total = 6;
        for (int i = 0; i < total; i++) {
            User customer = demoCustomer("고객" + i, "이름" + i);
            UUID room = openRoom(customer);
            say(room, customer, "문의 " + i);
            awaitDispatch(room);
            makeDue(room);
        }

        // when
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> returned = new ArrayList<>();
        try {
            List<Callable<JsonNode>> calls = List.of(() -> pending(4), () -> pending(4));
            for (Future<JsonNode> future : pool.invokeAll(calls)) {
                future.get().forEach(item -> returned.add(item.path("eventId").asText()));
            }
        } finally {
            pool.shutdown();
        }
        pending(20).forEach(item -> returned.add(item.path("eventId").asText()));

        // then: 6건 모두 정확히 한 번씩, lease도 한 번씩
        assertThat(returned).hasSize(total).doesNotHaveDuplicates();
        assertThat(botDispatchRepository.findAll()).hasSize(total).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(BotDispatchStatus.LEASED);
            assertThat(d.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("대기 문의가 없으면 본문은 래핑 없이 정확히 {\"items\":[]}")
    void pending_empty_bareItemsBody() throws Exception {
        mockMvc.perform(get("/api/bot/pending").header(HttpHeaders.AUTHORIZATION, "Bearer " + REPLY_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}", true));
    }

    @Test
    @DisplayName("lease 직전 다시 확인: 데모에서 빠지거나 허용 목록에서 빠진 고객 건은 반환하지 않고 이관 + 고정 안내")
    void pending_customerNoLongerTarget_handedOffWithNotice() throws Exception {
        // given
        User demo = demoCustomer("고객A", "김에이");
        User listed = user("목록고객", false);
        ReflectionTestUtils.setField(botDispatchService, "allowedUserIds", Set.of(listed.getId()));
        UUID demoRoom = openRoom(demo);
        UUID listedRoom = openRoom(listed);
        say(demoRoom, demo, "문의");
        say(listedRoom, listed, "문의");
        awaitDispatch(demoRoom);
        awaitDispatch(listedRoom);
        makeDue(demoRoom);
        makeDue(listedRoom);
        jdbcTemplate.update("UPDATE users SET is_demo = false WHERE id = ?", demo.getId());
        ReflectionTestUtils.setField(botDispatchService, "allowedUserIds", Set.of());

        // when
        JsonNode items = pending(null);

        // then
        assertThat(items).isEmpty();
        for (UUID room : List.of(demoRoom, listedRoom)) {
            BotDispatch dispatch = dispatchOf(room);
            assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
            assertThat(dispatch.getHandoffReason()).isEqualTo("자동응답 대상 아님");
            assertThat(dispatch.getAttempts()).isZero();
            assertThat(room(room).isNeedsHuman()).isTrue();
            assertThat(openCsCases(room)).hasSize(1);
        }
        assertThat(botMessages(demoRoom, demo)).extracting(ChatMessageResponse::getContent)
                .containsExactly(BotDispatchService.HANDOFF_NOTICE);
        assertThat(botMessages(listedRoom, listed)).extracting(ChatMessageResponse::getContent)
                .containsExactly(BotDispatchService.HANDOFF_NOTICE);
    }

    @Test
    @DisplayName("본문을 만들 수 없는 건(고객이 메시지 삭제)은 반환하지 않고 바로 FAILED + 이관, 나머지는 정상 반환")
    void pending_payloadBuildFails_failedAndHandedOffImmediately() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        User b = demoCustomer("고객B", "박비");
        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        UUID deleted = say(aRoom, a, "지울 문의");
        say(bRoom, b, "B 문의");
        awaitDispatch(aRoom);
        awaitDispatch(bRoom);
        chatService.deleteMessage(deleted, a.getId(), false);
        makeDue(aRoom);
        makeDue(bRoom);

        // when
        JsonNode items = pending(null);

        // then
        assertThat(items).singleElement()
                .satisfies(item -> assertThat(item.path("conversationId").asText()).isEqualTo(bRoom.toString()));
        BotDispatch failed = dispatchOf(aRoom);
        assertThat(failed.getStatus()).isEqualTo(BotDispatchStatus.FAILED);
        assertThat(failed.getHandoffReason()).isEqualTo("문의 내용 조회 실패");
        assertThat(room(aRoom).isNeedsHuman()).isTrue();
        assertThat(openCsCases(aRoom)).hasSize(1);
        assertThat(botMessages(aRoom, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly(BotDispatchService.HANDOFF_NOTICE);
        assertThat(dispatchOf(bRoom).getStatus()).isEqualTo(BotDispatchStatus.LEASED);
    }

    @Test
    @DisplayName("lease 기한이 지나면 PENDING으로 돌아가 다시 조회되고 attempts가 늘어난다")
    void leaseExpired_returnsToPending_refetchable_attemptsIncremented() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch first = leasedDispatch(room, a, "문의");

        // when: 기한 전에는 그대로
        botDispatchService.expireLeases(LocalDateTime.now());

        // then
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.LEASED);
        assertThat(pending(null)).isEmpty();

        // when: 기한 경과
        expireLease(room);
        botDispatchService.expireLeases(LocalDateTime.now());

        // then
        BotDispatch requeued = dispatchOf(room);
        assertThat(requeued.getStatus()).isEqualTo(BotDispatchStatus.PENDING);
        assertThat(requeued.getLeasedUntil()).isNull();
        assertThat(requeued.getAttempts()).isEqualTo(1);
        JsonNode items = pending(null);
        assertThat(items).singleElement()
                .satisfies(item -> assertThat(item.path("eventId").asText()).isEqualTo(first.getEventId().toString()));
        BotDispatch again = dispatchOf(room);
        assertThat(again.getStatus()).isEqualTo(BotDispatchStatus.LEASED);
        assertThat(again.getAttempts()).isEqualTo(2);
        assertThat(botMessages(room, a)).isEmpty();
    }

    @Test
    @DisplayName("lease 3회가 모두 만료되면 FAILED + 담당자 이관·고정 안내")
    void attemptsExceeded_failedAndHandedOff() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        leasedDispatch(room, a, "문의");

        // when: 1·2회째 만료 → 다시 PENDING, 3회째 만료 → FAILED
        for (int lease = 1; lease <= 2; lease++) {
            expireLease(room);
            botDispatchService.expireLeases(LocalDateTime.now());
            assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.PENDING);
            assertThat(pending(null)).hasSize(1);
        }
        expireLease(room);
        botDispatchService.expireLeases(LocalDateTime.now());

        // then
        BotDispatch failed = dispatchOf(room);
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getStatus()).isEqualTo(BotDispatchStatus.FAILED);
        assertThat(failed.getHandoffReason()).isEqualTo(BotDispatch.LEASE_EXHAUSTED_REASON);
        assertThat(room(room).isNeedsHuman()).isTrue();
        assertThat(openCsCases(room)).singleElement()
                .satisfies(c -> assertThat(c.getReason()).isEqualTo("문의방 " + room + ": " + BotDispatch.LEASE_EXHAUSTED_REASON));
        assertThat(botMessages(room, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly(BotDispatchService.HANDOFF_NOTICE);
        assertThat(pending(null)).isEmpty();
    }

    @Test
    @DisplayName("디바운스가 끝난 뒤 5분 동안 아무도 가져가지 않으면 응답 시간 초과로 이관 + 고정 안내")
    void pendingUnclaimed5Minutes_handsOff() {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        LocalDateTime sendAfter = awaitDispatch(room).getSendAfter();

        // when
        botDispatchService.handOffUnclaimed(sendAfter.plusMinutes(5).minusSeconds(1));
        BotDispatchStatus beforeTimeout = dispatchOf(room).getStatus();
        botDispatchService.handOffUnclaimed(sendAfter.plusMinutes(5).plusSeconds(1));

        // then
        assertThat(beforeTimeout).isEqualTo(BotDispatchStatus.PENDING);
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(dispatch.getHandoffReason()).isEqualTo("응답 시간 초과");
        assertThat(room(room).isNeedsHuman()).isTrue();
        assertThat(openCsCases(room)).singleElement()
                .satisfies(c -> assertThat(c.getReason()).isEqualTo("문의방 " + room + ": 응답 시간 초과"));
        assertThat(botMessages(room, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly(BotDispatchService.HANDOFF_NOTICE);
    }

    @Test
    @DisplayName("관리자 전역 토글이 꺼져 있어도 미처리 타임아웃 이관은 동작한다")
    void pendingUnclaimed_toggleOff_stillHandsOff() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        LocalDateTime sendAfter = awaitDispatch(room).getSendAfter();
        botDispatchService.updateSettings(false, admin.getId());

        // when
        botDispatchService.handOffUnclaimed(sendAfter.plusMinutes(6));

        // then
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(room(room).isNeedsHuman()).isTrue();
        botDispatchService.updateSettings(true, admin.getId());
    }

    @Test
    @DisplayName("limit은 1~20으로 클램프되고 기본은 10")
    void pending_limitClampedTo20() throws Exception {
        // given
        int total = 32;
        for (int i = 0; i < total; i++) {
            User customer = demoCustomer("고객" + i, "이름" + i);
            UUID room = openRoom(customer);
            say(room, customer, "문의 " + i);
        }
        await().atMost(Duration.ofSeconds(15)).until(() -> botDispatchRepository.findAll().size() == total);
        jdbcTemplate.update("UPDATE bot_dispatch SET send_after = ? WHERE status = 'PENDING'",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)));

        // when & then
        assertThat(pending(0)).hasSize(1);
        assertThat(pending(null)).hasSize(10);
        assertThat(pending(100)).hasSize(20);
        assertThat(pending(100)).hasSize(1);
    }

    @Test
    @DisplayName("봇 토큰이 틀리거나 없거나 일반 JWT(관리자 포함)면 대기 문의 조회는 401, lease도 하지 않는다")
    void pending_wrongToken_401() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        awaitDispatch(room);
        makeDue(room);
        String adminJwt = jwtTokenProvider.generateAccessToken(new UserPrincipal(admin.getId(), admin.getEmail(), true));

        // when & then
        mockMvc.perform(get("/api/bot/pending").header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/bot/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bot/pending").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt))
                .andExpect(status().isUnauthorized());
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.PENDING);
        assertThat(dispatch.getAttempts()).isZero();
    }

    @Test
    @DisplayName("lease 직전 다시 확인: 담당자가 답한 방은 반환하지 않고 '담당자 응대 중'으로 닫는다(안내 없음)")
    void pending_pausedRoom_closedNotReturned() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의드려요");
        awaitDispatch(room);
        chatService.sendMessage(room, admin.getId(), true, text("담당자가 바로 볼게요."));
        makeDue(room);

        // when
        JsonNode items = pending(null);

        // then
        assertThat(items).isEmpty();
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(dispatch.getHandoffReason()).isEqualTo("담당자 응대 중");
        assertThat(botMessages(room, a)).isEmpty();
        assertThat(openCsCases(room)).isEmpty();
    }

    @Test
    @DisplayName("lease 직전 다시 확인: 관리자 전역 토글이 꺼져 있으면 아무것도 반환하지 않는다")
    void pending_toggleOff_empty() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        awaitDispatch(room);
        makeDue(room);
        botDispatchService.updateSettings(false, admin.getId());

        // when & then
        assertThat(pending(null)).isEmpty();
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.PENDING);
        botDispatchService.updateSettings(true, admin.getId());
    }

    @Test
    @DisplayName("담당자 응대 중인 방의 lease가 만료되면 안내·이관 없이 닫는다")
    void leaseExpiredInPausedRoom_noNotice() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        leasedDispatch(room, a, "문의");
        chatService.sendMessage(room, admin.getId(), true, text("담당자입니다."));

        // when
        expireLease(room);
        botDispatchService.expireLeases(LocalDateTime.now());

        // then
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(dispatch.getHandoffReason()).isEqualTo("담당자 응대 중");
        assertThat(botMessages(room, a)).isEmpty();
        assertThat(room(room).isNeedsHuman()).isFalse();
        assertThat(openCsCases(room)).isEmpty();
    }

    // ── Slack 신호 ──

    @Test
    @DisplayName("Slack 신호 본문은 정확히 {\"text\":\"new-inquiry\"}이고 고객 정보·메시지·ID가 없다. 디바운스 중엔 보내지 않는다")
    void slackSignal_bodyHasNoCustomerDataOrIds() {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        UUID messageId = say(room, a, "이용권 환불 문의드려요");
        BotDispatch dispatch = awaitDispatch(room);

        // when: 디바운스 중
        botSignalService.signalIfDue(LocalDateTime.now());

        // then
        assertThat(slack.findAll(postRequestedFor(anyUrl()))).isEmpty();

        // when: pull 가능
        makeDue(room);
        botSignalService.signalIfDue(LocalDateTime.now());

        // then
        slack.verify(1, postRequestedFor(urlEqualTo(SLACK_PATH))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalTo("{\"text\":\"new-inquiry\"}")));
        LoggedRequest request = slack.findAll(postRequestedFor(anyUrl())).get(0);
        assertThat(request.getBodyAsString()).doesNotContain(a.getId().toString(), room.toString(), messageId.toString(),
                dispatch.getEventId().toString(), "고객A", "김에이", "환불", a.getEmail());
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("30초 안에 새로 pull 가능해진 건은 한 신호로 묶고, 30초 뒤 한 번 더 보낸다. 새 건이 없으면 보내지 않는다")
    void slackSignal_coalescedWithin30s() {
        // given
        User a = demoCustomer("고객A", "김에이");
        User b = demoCustomer("고객B", "박비");
        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        say(aRoom, a, "A 문의");
        awaitDispatch(aRoom);
        makeDue(aRoom);
        LocalDateTime t0 = LocalDateTime.now();
        botSignalService.signalIfDue(t0);
        say(bRoom, b, "B 문의");
        awaitDispatch(bRoom);
        jdbcTemplate.update("UPDATE bot_dispatch SET send_after = ? WHERE conversation_id = ?",
                Timestamp.valueOf(t0.plusSeconds(5)), bRoom);

        // when & then
        botSignalService.signalIfDue(t0.plusSeconds(10));
        slack.verify(1, postRequestedFor(urlEqualTo(SLACK_PATH)));
        botSignalService.signalIfDue(t0.plusSeconds(31));
        slack.verify(2, postRequestedFor(urlEqualTo(SLACK_PATH)));
        botSignalService.signalIfDue(t0.plusSeconds(70));
        slack.verify(2, postRequestedFor(urlEqualTo(SLACK_PATH)));
    }

    @Test
    @DisplayName("Slack이 실패하면 최대 3회 재시도 후 포기하고 PENDING은 그대로, 30초 뒤 다시 신호한다")
    void slackFailure_retriesThenKeepsPending() {
        // given
        slack.stubFor(post(urlEqualTo(SLACK_PATH)).willReturn(aResponse().withStatus(500)));
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        awaitDispatch(room);
        makeDue(room);
        LocalDateTime t0 = LocalDateTime.now();

        // when
        botSignalService.signalIfDue(t0);

        // then: 최초 + 재시도 3회
        slack.verify(4, postRequestedFor(urlEqualTo(SLACK_PATH)));
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.PENDING);
        assertThat(dispatch.getAttempts()).isZero();

        // when: 30초 안엔 다시 보내지 않고, 지나면 다시 보낸다
        slack.stubFor(post(urlEqualTo(SLACK_PATH)).willReturn(ok("ok")));
        botSignalService.signalIfDue(t0.plusSeconds(10));
        slack.verify(4, postRequestedFor(urlEqualTo(SLACK_PATH)));
        botSignalService.signalIfDue(t0.plusSeconds(31));
        slack.verify(5, postRequestedFor(urlEqualTo(SLACK_PATH)));
    }

    // ── 답변 수신 ──

    @Test
    @DisplayName("lease 기한이 지난 뒤 온 답변은 409, 아무것도 하지 않는다")
    void reply_afterLeaseExpired_409() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "문의");
        expireLease(room);

        // when & then: 스케줄러가 돌기 전(LEASED, 기한 지남)
        reply(dispatch, "reply", "늦은 답변", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.LEASED);

        // when & then: 스케줄러가 PENDING으로 돌린 뒤
        botDispatchService.expireLeases(LocalDateTime.now());
        reply(dispatch, "reply", "늦은 답변", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(botMessages(room, a)).isEmpty();
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING(아직 조회 전)에 온 답변은 409")
    void reply_beforeLease_409() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        BotDispatch dispatch = awaitDispatch(room);

        // when & then
        reply(dispatch, "reply", "답변", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(botMessages(room, a)).isEmpty();
    }

    @Test
    @DisplayName("같은 메시지 생성 이벤트가 중복으로 와도 dispatch는 한 건, 조회도 한 번")
    void duplicateMessageEvent_dispatchedOnce() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        UUID messageId = say(room, a, "문의드려요");
        awaitDispatch(room);

        // when
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new ChatMessageCreatedEvent(room, messageId));
            eventPublisher.publishEvent(new ChatMessageCreatedEvent(room, messageId));
        });

        // then
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .until(() -> botDispatchRepository.findAll().size() == 1);
        assertThat(dispatchOf(room).getMessageId()).isEqualTo(messageId);
        makeDue(room);
        assertThat(pending(null)).hasSize(1);
        assertThat(pending(null)).isEmpty();
    }

    @Test
    @DisplayName("동시에 들어온 두 고객의 답변은 각자 자기 방에만 붙는다")
    void concurrentInquiries_eachReplyLandsInOwnRoom() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        User b = demoCustomer("고객B", "박비");
        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        BotDispatch aDispatch = leasedDispatch(aRoom, a, "A 문의");
        BotDispatch bDispatch = leasedDispatch(bRoom, b, "B 문의");

        // when
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> aStatus = pool.submit(() -> reply(aDispatch, "reply", "A님 답변", null, REPLY_TOKEN)
                    .andReturn().getResponse().getStatus());
            Future<Integer> bStatus = pool.submit(() -> reply(bDispatch, "reply", "B님 답변", null, REPLY_TOKEN)
                    .andReturn().getResponse().getStatus());
            assertThat(aStatus.get()).isEqualTo(200);
            assertThat(bStatus.get()).isEqualTo(200);
        } finally {
            pool.shutdown();
        }

        // then
        assertThat(botMessages(aRoom, a)).extracting(ChatMessageResponse::getContent).containsExactly("A님 답변");
        assertThat(botMessages(bRoom, b)).extracting(ChatMessageResponse::getContent).containsExactly("B님 답변");
        assertThat(dispatchOf(aRoom).getStatus()).isEqualTo(BotDispatchStatus.REPLIED);
        assertThat(dispatchOf(bRoom).getStatus()).isEqualTo(BotDispatchStatus.REPLIED);
    }

    @Test
    @DisplayName("같은 eventId로 두 번 답하면 두 번째는 409, 메시지는 하나")
    void reply_sameEventIdTwice_secondIs409_singleMessage() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "이용권 몇 회 남았나요?");

        // when & then
        reply(dispatch, "reply", "3회 남았어요.", null, REPLY_TOKEN).andExpect(status().isOk());
        reply(dispatch, "reply", "3회 남았어요.", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(botMessages(room, a)).hasSize(1);
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.REPLIED);
    }

    @Test
    @DisplayName("봇 토큰이 틀리거나 없거나 일반 JWT(관리자 포함)만 있으면 401")
    void reply_wrongToken_401() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "문의");
        String adminJwt = jwtTokenProvider.generateAccessToken(new UserPrincipal(admin.getId(), admin.getEmail(), true));

        // when & then
        reply(dispatch, "reply", "답변", null, "wrong-token").andExpect(status().isUnauthorized());
        reply(dispatch, "reply", "답변", null, null).andExpect(status().isUnauthorized());
        reply(dispatch, "reply", "답변", null, adminJwt).andExpect(status().isUnauthorized());
        assertThat(botMessages(room, a)).isEmpty();
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.LEASED);
    }

    @Test
    @DisplayName("다른 방의 conversationId로 답하면 403")
    void reply_otherRoomConversationId_403() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        User b = demoCustomer("고객B", "박비");
        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        BotDispatch dispatch = leasedDispatch(aRoom, a, "문의");

        // when & then
        mockMvc.perform(replyRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + REPLY_TOKEN)
                        .header("Idempotency-Key", dispatch.getEventId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyBody(dispatch.getEventId(), bRoom,
                                dispatch.getMessageId(), "reply", "남의 방", null))))
                .andExpect(status().isForbidden());
        assertThat(botMessages(bRoom, b)).isEmpty();
        assertThat(botMessages(aRoom, a)).isEmpty();
    }

    @Test
    @DisplayName("Idempotency-Key가 eventId와 다르면 422")
    void reply_idempotencyKeyMismatch_422() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "문의");

        // when & then
        mockMvc.perform(replyRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + REPLY_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyBody(dispatch.getEventId(), room,
                                dispatch.getMessageId(), "reply", "답변", null))))
                .andExpect(status().isUnprocessableEntity());
        reply(dispatch, "reply", " ", null, REPLY_TOKEN).andExpect(status().isUnprocessableEntity());
        reply(dispatch, "reply", "가".repeat(2001), null, REPLY_TOKEN).andExpect(status().isUnprocessableEntity());
        assertThat(botMessages(room, a)).isEmpty();
    }

    @Test
    @DisplayName("답변 본문은 < > & \" ' 다섯 개만 이스케이프하고 …·“”·한글은 그대로 저장한다")
    void reply_htmlText_escaped() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "문의");

        // when
        reply(dispatch, "reply", "…“”한글 <b>&</b> \"q\" 'a'", null, REPLY_TOKEN).andExpect(status().isOk());

        // then
        assertThat(botMessages(room, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly("…“”한글 &lt;b&gt;&amp;&lt;/b&gt; &quot;q&quot; &#39;a&#39;");
    }

    @Test
    @DisplayName("같은 eventId 답변이 두 스레드에서 동시에 와도 하나만 200, 메시지는 하나")
    void reply_sameEventIdConcurrently_singleMessage() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "문의");

        // when
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> reply(dispatch, "reply", "답변", null, REPLY_TOKEN).andReturn().getResponse().getStatus(),
                    () -> reply(dispatch, "reply", "답변", null, REPLY_TOKEN).andReturn().getResponse().getStatus());
            for (Future<Integer> future : pool.invokeAll(calls)) {
                statuses.add(future.get());
            }
        } finally {
            pool.shutdown();
        }

        // then
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(botMessages(room, a)).hasSize(1);
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.REPLIED);
    }

    @Test
    @DisplayName("LEASED 중 담당자가 답하면 봇 답변은 409, 메시지 없음")
    void adminRepliesWhileLeased_botReply409() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "문의");
        chatService.sendMessage(room, admin.getId(), true, text("담당자입니다."));

        // when & then
        reply(dispatch, "reply", "봇 답변", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(botMessages(room, a)).isEmpty();
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.LEASED);
    }

    @Test
    @DisplayName("환불 요청에 봇이 handoff로 답하면 봇 문구 게시 + needsHuman + CS 예외, 사유는 관리자에게만")
    void refundRequest_handoffReply_marksNeedsHuman() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "환불해 주세요");

        // when
        reply(dispatch, "handoff", "환불은 담당자가 확인 후 안내드릴게요.", "환불 요청", REPLY_TOKEN)
                .andExpect(status().isOk());

        // then
        BotDispatch handedOff = dispatchOf(room);
        assertThat(handedOff.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(handedOff.getHandoffReason()).isEqualTo("환불 요청");
        assertThat(room(room).isNeedsHuman()).isTrue();
        assertThat(openCsCases(room)).singleElement()
                .satisfies(c -> assertThat(c.getReason()).isEqualTo("문의방 " + room + ": 환불 요청"));
        assertThat(botMessages(room, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly("환불은 담당자가 확인 후 안내드릴게요.");

        ChatRoomSummaryResponse adminView = adminChatService.listRooms(admin.getId(), true).stream()
                .filter(r -> r.getId().equals(room)).findFirst().orElseThrow();
        assertThat(adminView.isNeedsHuman()).isTrue();
        assertThat(adminView.getHandoffReason()).isEqualTo("환불 요청");
        assertThat(adminChatService.listRooms(admin.getId(), true)).hasSize(1);
        ChatRoomSummaryResponse customerView = chatService.listRooms(a.getId(), false).get(0);
        assertThat(customerView.isNeedsHuman()).isTrue();
        assertThat(customerView.getHandoffReason()).isNull();
        assertThat(chatService.getRoom(room, admin.getId(), true).getHandoffReason()).isEqualTo("환불 요청");
        assertThat(chatService.getRoom(room, a.getId(), false).getHandoffReason()).isNull();
    }

    @Test
    @DisplayName("LEASED 중 고객이 새로 보내면 새 PENDING이 생기고 이전 LEASED 답변은 409, 만료되면 '대체됨'으로 안내 없이 닫는다")
    void reply_staleDispatch_409() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch old = leasedDispatch(room, a, "첫 질문");
        UUID newer = say(room, a, "추가 질문");
        await().atMost(Duration.ofSeconds(5)).until(() -> botDispatchRepository.findAll().size() == 2);

        // when & then
        reply(old, "reply", "첫 질문 답", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(botDispatchRepository.findByEventId(old.getEventId()).orElseThrow().getStatus())
                .isEqualTo(BotDispatchStatus.LEASED);
        assertThat(botDispatchRepository.findAll()).filteredOn(d -> d.getMessageId().equals(newer))
                .singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(BotDispatchStatus.PENDING));
        assertThat(botMessages(room, a)).isEmpty();
        // 대체된 LEASED는 만료되면 PENDING으로 돌리지 않고(방당 PENDING 1건 유지) 안내·이관 없이 닫는다
        jdbcTemplate.update("UPDATE bot_dispatch SET leased_until = ? WHERE event_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), old.getEventId());
        botDispatchService.expireLeases(LocalDateTime.now());
        BotDispatch closed = botDispatchRepository.findByEventId(old.getEventId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(closed.getHandoffReason()).isEqualTo("대체됨");
        assertThat(botDispatchRepository.findAll()).filteredOn(d -> d.getMessageId().equals(newer))
                .singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(BotDispatchStatus.PENDING));
        assertThat(room(room).isNeedsHuman()).isFalse();
        assertThat(openCsCases(room)).isEmpty();
        assertThat(botMessages(room, a)).isEmpty();
    }

    // ── 발송 큐(기존 규칙 유지) ──

    @Test
    @DisplayName("10초 안에 세 번 보내면 dispatch 한 건, message_id는 마지막 메시지")
    void debounce_threeMessagesWithin10s_singleDispatchWithLastMessageId() {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);

        // when
        say(room, a, "안녕하세요");
        say(room, a, "질문이 있는데요");
        UUID last = say(room, a, "이용권 환불 되나요?");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            List<BotDispatch> all = botDispatchRepository.findAll();
            return all.size() == 1 && all.get(0).getMessageId().equals(last);
        });
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.PENDING);
        assertThat(dispatch.getSendAfter()).isAfter(LocalDateTime.now().plusSeconds(5));
    }

    @Test
    @DisplayName("관리자가 문의방에 답하면 botPaused, 이후 고객 메시지는 큐에 넣지 않는다. 재개하면 플래그·CS 예외 정리")
    void adminMessage_pausesBot() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = leasedDispatch(room, a, "환불해 주세요");
        reply(dispatch, "handoff", "담당자에게 넘길게요.", "환불 요청", REPLY_TOKEN).andExpect(status().isOk());

        // when
        chatService.sendMessage(room, admin.getId(), true, text("담당자입니다. 확인해 볼게요."));
        say(room, a, "네 기다릴게요");

        // then
        assertThat(room(room).isBotPaused()).isTrue();
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .until(() -> botDispatchRepository.findAll().size() == 1);

        // when: 재개
        adminChatService.resumeBot(room, admin.getId(), true);

        // then
        ChatRoom resumed = room(room);
        assertThat(resumed.isBotPaused()).isFalse();
        assertThat(resumed.isNeedsHuman()).isFalse();
        assertThat(openCsCases(room)).isEmpty();
        assertThat(exceptionCaseRepository.findAll()).singleElement()
                .satisfies(c -> assertThat(c.getStatus()).isEqualTo(ExceptionCaseStatus.RESOLVED));
    }

    @Test
    @DisplayName("데모도 허용 목록도 아닌 고객은 큐에 넣지 않고, 허용 목록에 넣으면 넣는다")
    void nonAllowlistedCustomer_noDispatch() {
        // given
        User c = user("일반고객", false);
        UUID room = openRoom(c);

        // when
        say(room, c, "문의드려요");

        // then
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .until(() -> botDispatchRepository.findAll().isEmpty());

        // when: 허용 목록
        ReflectionTestUtils.setField(botDispatchService, "allowedUserIds", Set.of(c.getId()));
        say(room, c, "다시 문의드려요");

        // then
        awaitDispatch(room);
    }

    @Test
    @DisplayName("데모 고객 메시지는 10초 디바운스 뒤 조회 가능한 PENDING으로 쌓이고, 디바운스 중엔 조회되지 않는다")
    void demoCustomer_dispatched() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);

        // when
        UUID messageId = say(room, a, "문의드려요");

        // then
        BotDispatch dispatch = awaitDispatch(room);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.PENDING);
        assertThat(dispatch.getMessageId()).isEqualTo(messageId);
        assertThat(dispatch.getCustomerId()).isEqualTo(a.getId());
        assertThat(dispatch.getEventId()).isNotNull();
        assertThat(dispatch.getSendAfter()).isBetween(LocalDateTime.now().plusSeconds(5), LocalDateTime.now().plusSeconds(11));
        assertThat(pending(null)).isEmpty();
    }

    @Test
    @DisplayName("최근 1시간 20건이면 큐에 넣지 않고 이관 + 고정 안내")
    void hourlyLimitExceeded_handsOff() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        for (int i = 0; i < 20; i++) {
            BotDispatch past = BotDispatch.pending(room, UUID.randomUUID(), a.getId(), LocalDateTime.now());
            past.lease(LocalDateTime.now());
            past.markReplied();
            botDispatchRepository.save(past);
        }

        // when
        UUID messageId = say(room, a, "또 문의드려요");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> botDispatchRepository.findAll().size() == 21);
        BotDispatch limited = botDispatchRepository.findAll().stream()
                .filter(d -> d.getMessageId().equals(messageId)).findFirst().orElseThrow();
        assertThat(limited.getStatus()).isEqualTo(BotDispatchStatus.HANDED_OFF);
        assertThat(limited.getHandoffReason()).isEqualTo("발송 한도 초과");
        assertThat(room(room).isNeedsHuman()).isTrue();
        assertThat(openCsCases(room)).hasSize(1);
        await().atMost(Duration.ofSeconds(3)).until(() -> botMessages(room, a).size() == 1);
        assertThat(botMessages(room, a).get(0).getContent()).isEqualTo(BotDispatchService.HANDOFF_NOTICE);
        assertThat(pending(null)).isEmpty();
    }

    // ── fixtures ──

    private User user(String nickname, boolean demo) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder().email(key + "@example.com").password("encoded").name(nickname + "이름")
                .gender(Gender.FEMALE).age(30).nickname(nickname).phone("01012345678").build();
        user.approveRandomTable();
        if (demo) {
            user.markDemo();
        }
        return userRepository.save(user);
    }

    private User demoCustomer(String nickname, String name) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        String phone = "010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        User user = User.builder().email(key + "@example.com").password("encoded").name(name)
                .gender(Gender.FEMALE).age(30).nickname(nickname).phone(phone).build();
        user.approveRandomTable();
        user.markDemo();
        return userRepository.save(user);
    }

    private Application application(User user) {
        return applicationRepository.save(Application.builder().bookingNumber("WH-" + user.getId().toString().substring(0, 8))
                .gathering(gathering).user(user).name(user.getName()).phone(user.getPhone()).build());
    }

    private void activePass(User user, int sessionCount) {
        TicketProductOption product = ticketProductRepository.save(new TicketProductOption("3회권", sessionCount, 29700));
        TicketPass pass = ticketPassRepository.save(new TicketPass(user, null, product));
        adminTicketService.confirm(pass.getId());
    }

    private UUID openRoom(User user) {
        return chatService.openInquiryRoom(user.getId(), false).getRoomId();
    }

    private UUID say(UUID roomId, User user, String content) {
        return chatService.sendMessage(roomId, user.getId(), false, text(content)).getId();
    }

    private ChatMessageSendRequest text(String content) {
        ChatMessageSendRequest request = new ChatMessageSendRequest();
        ReflectionTestUtils.setField(request, "type", ChatMessageType.TEXT);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }

    private BotDispatch awaitDispatch(UUID roomId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> botDispatchRepository.findAll().stream()
                .anyMatch(d -> d.getConversationId().equals(roomId)));
        return dispatchOf(roomId);
    }

    // 디바운스(10초)가 끝난 것처럼 이 방의 PENDING을 지금 조회 가능하게 당긴다.
    private void makeDue(UUID roomId) {
        jdbcTemplate.update("UPDATE bot_dispatch SET send_after = ? WHERE conversation_id = ? AND status = 'PENDING'",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), roomId);
    }

    // 이 방의 LEASED 기한을 지난 것으로 당긴다.
    private void expireLease(UUID roomId) {
        jdbcTemplate.update("UPDATE bot_dispatch SET leased_until = ? WHERE conversation_id = ? AND status = 'LEASED'",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), roomId);
    }

    // 방의 최신 dispatch
    private BotDispatch dispatchOf(UUID roomId) {
        return botDispatchRepository.findAll().stream()
                .filter(d -> d.getConversationId().equals(roomId))
                .max((x, y) -> x.getCreatedAt().compareTo(y.getCreatedAt()))
                .orElseThrow();
    }

    private BotDispatch leasedDispatch(UUID roomId, User user, String content) throws Exception {
        say(roomId, user, content);
        awaitDispatch(roomId);
        makeDue(roomId);
        assertThat(pending(null)).anyMatch(item -> item.path("conversationId").asText().equals(roomId.toString()));
        BotDispatch dispatch = dispatchOf(roomId);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.LEASED);
        return dispatch;
    }

    // 본문은 래핑 없는 {"items":[...]}(R20). 최상위 키가 items 하나뿐인지 매번 확인한다.
    private JsonNode pending(Integer limit) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/bot/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + REPLY_TOKEN);
        if (limit != null) {
            request.param("limit", String.valueOf(limit));
        }
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(body);
        List<String> keys = new ArrayList<>();
        root.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("items");
        assertThat(root.path("items").isArray()).isTrue();
        return root.path("items");
    }

    private static MockHttpServletRequestBuilder replyRequest() {
        return MockMvcRequestBuilders.post("/api/bot/replies");
    }

    private JsonNode itemOf(JsonNode items, UUID customerId) {
        for (JsonNode item : items) {
            if (item.path("customer").path("id").asText().equals(customerId.toString())) {
                return item;
            }
        }
        throw new AssertionError("no item for customer");
    }

    private ResultActions reply(BotDispatch dispatch, String action, String text, String handoffReason, String token)
            throws Exception {
        var request = replyRequest()
                .header("Idempotency-Key", dispatch.getEventId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(replyBody(dispatch.getEventId(), dispatch.getConversationId(),
                        dispatch.getMessageId(), action, text, handoffReason)));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private Map<String, Object> replyBody(UUID eventId, UUID conversationId, UUID inReplyTo, String action,
                                          String text, String handoffReason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("conversationId", conversationId);
        body.put("inReplyToMessageId", inReplyTo);
        body.put("action", action);
        body.put("text", text);
        body.put("handoffReason", handoffReason);
        return body;
    }

    private List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isValueNode()) {
            values.add(node.asText());
        }
        node.forEach(child -> values.addAll(textValues(child)));
        return values;
    }

    private List<ChatMessageResponse> botMessages(UUID roomId, User viewer) {
        return new ArrayList<>(chatService.listMessages(roomId, viewer.getId(), false, null, null, 100)).stream()
                .filter(ChatMessageResponse::isAiGenerated)
                .toList();
    }

    private ChatRoom room(UUID roomId) {
        return chatRoomRepository.findById(roomId).orElseThrow();
    }

    private List<ExceptionCase> openCsCases(UUID roomId) {
        return exceptionCaseRepository.findAll().stream()
                .filter(c -> c.getType() == ExceptionCaseType.CS && c.getStatus() == ExceptionCaseStatus.OPEN)
                .filter(c -> c.getReason().startsWith("문의방 " + roomId + ":"))
                .toList();
    }
}
