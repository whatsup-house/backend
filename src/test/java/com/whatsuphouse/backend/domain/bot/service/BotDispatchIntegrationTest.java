package com.whatsuphouse.backend.domain.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 그록봇 자동응답(KAN-406) 통합 테스트: 고객 메시지 → 디바운스 큐 → 웹훅 발송(MockRestServiceServer) → 답변 수신 API.
 * 합성 고객 A(신청 입금확인 완료·이용권 잔여 3회)와 B(신청 입금 대기·이용권 없음)만 쓴다. 외부 네트워크는 호출하지 않는다.
 * 스케줄러는 목으로 막고 sendDue/handOffTimedOut에 시각을 넘겨 재시도·타임아웃을 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bot.enabled=true",
        "bot.webhook-url=http://grok.test/webhook",
        "bot.webhook-key=test-webhook-key",
        "bot.reply-token=test-reply-token"
})
class BotDispatchIntegrationTest {

    private static final String WEBHOOK_URL = "http://grok.test/webhook";
    private static final String REPLY_TOKEN = "test-reply-token";

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
    private BotWebhookClient botWebhookClient;
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

    private MockRestServiceServer webhook;
    private final List<String> webhookBodies = new CopyOnWriteArrayList<>();
    private Gathering gathering;
    private User admin;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        webhook = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(botWebhookClient, "restClient", builder.build());
        webhookBodies.clear();
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
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    @DisplayName("A·B 동시 발송: A 페이로드엔 A 본인 데이터만 있고 B의 id·이름·신청 id는 없다")
    void payload_customerA_containsNoCustomerBData() throws Exception {
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
        expectWebhook(2, HttpStatus.OK);

        // when
        sendDue();

        // then
        webhook.verify();
        assertThat(webhookBodies).hasSize(2);
        String aBody = bodyOf(a.getId());
        String bBody = bodyOf(b.getId());
        assertThat(aBody).doesNotContain(b.getId().toString(), "고객B", "박비", bApplication.getId().toString(), bRoom.toString());
        assertThat(aBody).doesNotContain(a.getEmail(), "\"email\"");
        assertThat(bBody).doesNotContain(a.getId().toString(), "고객A", "김에이", aApplication.getId().toString());

        JsonNode aJson = objectMapper.readTree(aBody);
        assertThat(aJson.path("conversationId").asText()).isEqualTo(aRoom.toString());
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
        assertThat(aJson.path("replyUrl").asText()).isEqualTo("https://api.whatsup.house/api/bot/replies");

        JsonNode bJson = objectMapper.readTree(bBody);
        assertThat(bJson.path("context").path("applications").get(0).path("status").asText()).isEqualTo("PAYMENT_PENDING");
        assertThat(bJson.path("context").path("tickets").path("totalRemaining").asInt()).isZero();
        assertThat(dispatchOf(aRoom).getStatus()).isEqualTo(BotDispatchStatus.SENT);
        assertThat(dispatchOf(bRoom).getStatus()).isEqualTo(BotDispatchStatus.SENT);
    }

    @Test
    @DisplayName("같은 메시지 생성 이벤트가 중복으로 와도 dispatch는 한 건, 웹훅도 한 번")
    void duplicateMessageEvent_dispatchedOnce() {
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
        expectWebhook(1, HttpStatus.OK);
        sendDue();
        sendDue();
        webhook.verify();
    }

    @Test
    @DisplayName("동시에 들어온 두 고객의 답변은 각자 자기 방에만 붙는다")
    void concurrentInquiries_eachReplyLandsInOwnRoom() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        User b = demoCustomer("고객B", "박비");
        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        say(aRoom, a, "A 문의");
        say(bRoom, b, "B 문의");
        awaitDispatch(aRoom);
        awaitDispatch(bRoom);
        expectWebhook(2, HttpStatus.OK);
        sendDue();
        BotDispatch aDispatch = dispatchOf(aRoom);
        BotDispatch bDispatch = dispatchOf(bRoom);

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
    @DisplayName("웹훅 400·5xx·429가 이어지면 최초+3회 재시도 후 FAILED, 담당자 이관·고정 안내")
    void webhook400or5xx_retriesThenFailed_handsOff() {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "답변 주세요");
        awaitDispatch(room);
        expectWebhook(1, HttpStatus.BAD_REQUEST);
        expectWebhook(1, HttpStatus.INTERNAL_SERVER_ERROR);
        expectWebhook(1, HttpStatus.SERVICE_UNAVAILABLE);
        expectWebhook(1, HttpStatus.TOO_MANY_REQUESTS);
        LocalDateTime t0 = LocalDateTime.now().plusSeconds(11);

        // when: 백오프 5s → 30s → 2m. 대기 중에는 다시 보내지 않는다.
        botDispatchService.sendDue(t0);
        botDispatchService.sendDue(t0.plusSeconds(1));
        botDispatchService.sendDue(t0.plusSeconds(6));
        botDispatchService.sendDue(t0.plusSeconds(37));
        botDispatchService.sendDue(t0.plusSeconds(158));
        botDispatchService.sendDue(t0.plusSeconds(600));

        // then
        webhook.verify();
        BotDispatch dispatch = dispatchOf(room);
        assertThat(dispatch.getAttempts()).isEqualTo(4);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.FAILED);
        assertThat(dispatch.getLastError()).isEqualTo("HTTP 429").doesNotContain("test-webhook-key");
        assertThat(room(room).isNeedsHuman()).isTrue();
        assertThat(openCsCases(room)).hasSize(1);
        assertThat(botMessages(room, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly(BotDispatchService.HANDOFF_NOTICE);
    }

    @Test
    @DisplayName("같은 eventId로 두 번 답하면 두 번째는 409, 메시지는 하나")
    void reply_sameEventIdTwice_secondIs409_singleMessage() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = sentDispatch(room, a, "이용권 몇 회 남았나요?");

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
        BotDispatch dispatch = sentDispatch(room, a, "문의");
        String adminJwt = jwtTokenProvider.generateAccessToken(new UserPrincipal(admin.getId(), admin.getEmail(), true));

        // when & then
        reply(dispatch, "reply", "답변", null, "wrong-token").andExpect(status().isUnauthorized());
        reply(dispatch, "reply", "답변", null, null).andExpect(status().isUnauthorized());
        reply(dispatch, "reply", "답변", null, adminJwt).andExpect(status().isUnauthorized());
        assertThat(botMessages(room, a)).isEmpty();
        assertThat(dispatchOf(room).getStatus()).isEqualTo(BotDispatchStatus.SENT);
    }

    @Test
    @DisplayName("다른 방의 conversationId로 답하면 403")
    void reply_otherRoomConversationId_403() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        User b = demoCustomer("고객B", "박비");
        UUID aRoom = openRoom(a);
        UUID bRoom = openRoom(b);
        BotDispatch dispatch = sentDispatch(aRoom, a, "문의");

        // when & then
        mockMvc.perform(post("/api/bot/replies")
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
        BotDispatch dispatch = sentDispatch(room, a, "문의");

        // when & then
        mockMvc.perform(post("/api/bot/replies")
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
    @DisplayName("답변 본문의 HTML은 이스케이프해 저장한다")
    void reply_htmlText_escaped() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = sentDispatch(room, a, "문의");

        // when
        reply(dispatch, "reply", "<script>alert(1)</script>", null, REPLY_TOKEN).andExpect(status().isOk());

        // then
        assertThat(botMessages(room, a)).extracting(ChatMessageResponse::getContent)
                .containsExactly("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("발송 후 3분 동안 답이 없으면 응답 시간 초과로 이관")
    void sentWithoutReply3Minutes_handsOff() {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        say(room, a, "문의");
        awaitDispatch(room);
        expectWebhook(1, HttpStatus.OK);
        LocalDateTime sentAt = LocalDateTime.now().plusSeconds(11);
        botDispatchService.sendDue(sentAt);

        // when
        botDispatchService.handOffTimedOut(sentAt.plusMinutes(3).minusSeconds(1));
        BotDispatchStatus beforeTimeout = dispatchOf(room).getStatus();
        botDispatchService.handOffTimedOut(sentAt.plusMinutes(3).plusSeconds(1));

        // then
        assertThat(beforeTimeout).isEqualTo(BotDispatchStatus.SENT);
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
    @DisplayName("환불 요청에 봇이 handoff로 답하면 봇 문구 게시 + needsHuman + CS 예외, 사유는 관리자에게만")
    void refundRequest_handoffReply_marksNeedsHuman() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = sentDispatch(room, a, "환불해 주세요");

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
    @DisplayName("관리자가 문의방에 답하면 botPaused, 이후 고객 메시지는 발송하지 않는다. 재개하면 플래그·CS 예외 정리")
    void adminMessage_pausesBot() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch dispatch = sentDispatch(room, a, "환불해 주세요");
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
    @DisplayName("데모도 허용 목록도 아닌 고객은 발송하지 않고, 허용 목록에 넣으면 발송한다")
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
    @DisplayName("데모 고객 메시지는 10초 뒤 발송 대기(PENDING)로 쌓인다")
    void demoCustomer_dispatched() {
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
    }

    @Test
    @DisplayName("최근 1시간 발송 20건이면 보내지 않고 이관 + 고정 안내")
    void hourlyLimitExceeded_handsOff() {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        for (int i = 0; i < 20; i++) {
            BotDispatch past = BotDispatch.pending(room, UUID.randomUUID(), a.getId(), LocalDateTime.now());
            past.markSent(LocalDateTime.now());
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
        sendDue();
        webhook.verify();
    }

    @Test
    @DisplayName("SENT 이후 고객이 새로 보내 새 dispatch가 생기면 이전 dispatch 답변은 409, 상태 그대로")
    void reply_staleDispatch_409() throws Exception {
        // given
        User a = demoCustomer("고객A", "김에이");
        UUID room = openRoom(a);
        BotDispatch old = sentDispatch(room, a, "첫 질문");
        UUID newer = say(room, a, "추가 질문");
        await().atMost(Duration.ofSeconds(5)).until(() -> botDispatchRepository.findAll().size() == 2);

        // when & then
        reply(old, "reply", "첫 질문 답", null, REPLY_TOKEN).andExpect(status().isConflict());
        assertThat(botDispatchRepository.findByEventId(old.getEventId()).orElseThrow().getStatus())
                .isEqualTo(BotDispatchStatus.SENT);
        assertThat(botDispatchRepository.findAll()).filteredOn(d -> d.getMessageId().equals(newer))
                .singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(BotDispatchStatus.PENDING));
        assertThat(botMessages(room, a)).isEmpty();
        // 대체된 SENT는 응답 시간 초과로 이관하지 않는다
        botDispatchService.handOffTimedOut(LocalDateTime.now().plusMinutes(10));
        assertThat(room(room).isNeedsHuman()).isFalse();
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
        User user = User.builder().email(key + "@example.com").password("encoded").name(name)
                .gender(Gender.FEMALE).age(30).nickname(nickname).phone("01098765432").build();
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

    // 방의 최신 dispatch
    private BotDispatch dispatchOf(UUID roomId) {
        return botDispatchRepository.findAll().stream()
                .filter(d -> d.getConversationId().equals(roomId))
                .max((x, y) -> x.getCreatedAt().compareTo(y.getCreatedAt()))
                .orElseThrow();
    }

    private BotDispatch sentDispatch(UUID roomId, User user, String content) {
        say(roomId, user, content);
        awaitDispatch(roomId);
        expectWebhook(1, HttpStatus.OK);
        sendDue();
        BotDispatch dispatch = dispatchOf(roomId);
        assertThat(dispatch.getStatus()).isEqualTo(BotDispatchStatus.SENT);
        return dispatch;
    }

    private void sendDue() {
        botDispatchService.sendDue(LocalDateTime.now().plusSeconds(11));
    }

    private void expectWebhook(int times, HttpStatus status) {
        webhook.expect(ExpectedCount.times(times), requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-webhook-key"))
                .andExpect(request -> webhookBodies.add(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withStatus(status));
    }

    private String bodyOf(UUID customerId) {
        return webhookBodies.stream().filter(body -> {
            try {
                return objectMapper.readTree(body).path("customer").path("id").asText().equals(customerId.toString());
            } catch (Exception e) {
                return false;
            }
        }).findFirst().orElseThrow();
    }

    private ResultActions reply(BotDispatch dispatch, String action, String text, String handoffReason, String token)
            throws Exception {
        var request = post("/api/bot/replies")
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
