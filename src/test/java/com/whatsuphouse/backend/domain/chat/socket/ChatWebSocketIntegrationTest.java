package com.whatsuphouse.backend.domain.chat.socket;

import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageSendRequest;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.chat.service.LinkPreviewFetcher.LinkPreview;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.auth.JwtTokenProvider;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.CacheConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 실제 서버(랜덤 포트)에 STOMP 클라이언트로 붙어 인증·구독 권한·AFTER_COMMIT 브로드캐스트를 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ChatService chatService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private CacheManager cacheManager;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private UUID roomId;
    private UUID memberId;
    private UUID senderId;
    private UUID outsiderId;

    @BeforeEach
    void setUp() {
        memberId = saveUser("수신자");
        senderId = saveUser("발신자");
        outsiderId = saveUser("외부인");
        roomId = chatRoomRepository.save(ChatRoom.group("10월 게더링", null, null, senderId)).getId();
        chatMemberRepository.saveAll(List.of(ChatMember.join(roomId, memberId, null), ChatMember.join(roomId, senderId, null)));

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    // 서비스 트랜잭션이 실제로 커밋되므로(AFTER_COMMIT 검증) 직접 지운다.
    @AfterEach
    void tearDown() {
        // ERROR 프레임 뒤 서버가 먼저 닫은 세션은 isConnected가 잠깐 true로 남아 disconnect가 실패한다.
        // 그 예외로 아래 정리가 건너뛰어지면 공유 H2에 사용자가 남아 다른 테스트(AdminUserRepositoryTest)가 깨진다.
        sessions.stream().filter(StompSession::isConnected).forEach(session -> {
            try {
                session.disconnect();
            } catch (MessageDeliveryException alreadyClosed) {
                // 이미 닫힌 연결 — 정리만 계속한다.
            }
        });
        chatMessageRepository.deleteAll();
        chatMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
        userRepository.deleteAllById(List.of(memberId, senderId, outsiderId));
    }

    @Test
    @DisplayName("무효 토큰으로 CONNECT 하면 ERROR(INVALID_TOKEN)로 거부된다")
    void connect_invalidToken_rejected() throws Exception {
        // given
        ErrorCollector errors = new ErrorCollector();
        StompHeaders headers = new StompHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value");

        // when
        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), headers, errors);

        // then
        assertThat(errors.messages.poll(5, SECONDS)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("쿠키 인증으로 소켓 토큰(typ=chat-socket)을 발급받아 그 토큰으로 CONNECT 할 수 있다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void socketToken_cookieAuth_issuedAndConnects() throws Exception {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "accessToken=" + jwtTokenProvider.generateAccessToken(principal(memberId)));

        // when
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/chat/socket-token", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("expiresIn", 120);
        String token = (String) data.get("token");
        Map<String, Object> claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims).containsEntry("typ", "chat-socket").containsEntry("sub", memberId.toString());

        StompSession session = connectWith(token, new ErrorCollector());
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    @DisplayName("미인증으로 소켓 토큰을 요청하면 401")
    void socketToken_noAuth_returns401() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/api/chat/socket-token", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("소켓 토큰으로는 REST API를 인증할 수 없다(401)")
    void restApi_socketToken_returns401() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtTokenProvider.generateChatSocketToken(principal(memberId)));

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/chat/rooms", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("일반 access 토큰으로 CONNECT 하면 ERROR(UNAUTHORIZED)로 거부된다")
    void connect_accessToken_rejected() throws Exception {
        // given
        ErrorCollector errors = new ErrorCollector();
        String accessToken = jwtTokenProvider.generateAccessToken(principal(memberId));

        // when
        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), bearer(accessToken), errors);

        // then
        assertThat(errors.messages.poll(5, SECONDS)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("만료된 소켓 토큰으로 CONNECT 하면 ERROR(TOKEN_EXPIRED)로 거부된다")
    void connect_expiredSocketToken_rejected() throws Exception {
        // given: 같은 서명키, 수명 -1초
        ErrorCollector errors = new ErrorCollector();
        String expired = new JwtTokenProvider(jwtSecret, 0, 0, -1000).generateChatSocketToken(principal(memberId));

        // when
        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), bearer(expired), errors);

        // then
        assertThat(errors.messages.poll(5, SECONDS)).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("비멤버가 방 토픽을 구독하면 ERROR(CHAT_NOT_MEMBER)로 거부된다")
    void subscribe_nonMember_rejected() throws Exception {
        // given
        ErrorCollector errors = new ErrorCollector();
        StompSession session = connect(outsiderId, errors);

        // when
        session.subscribe(roomTopic(), new PayloadCollector());

        // then
        assertThat(errors.messages.poll(5, SECONDS)).isEqualTo("CHAT_NOT_MEMBER");
    }

    @Test
    @DisplayName("멤버는 메시지 생성 시 방 토픽으로 MESSAGE_CREATED(복호화 본문), 개인 큐로 방 목록 갱신을 받는다")
    @SuppressWarnings("unchecked")
    void messageCreated_member_receivesEvents() throws Exception {
        // given
        StompSession session = connect(memberId, new ErrorCollector());
        PayloadCollector topic = new PayloadCollector();
        PayloadCollector queue = new PayloadCollector();
        session.subscribe(roomTopic(), topic);
        session.subscribe("/user/queue/rooms", queue);
        awaitSubscribed(topic, () -> messagingTemplate.convertAndSend(roomTopic(), Map.of("probe", true)));
        awaitSubscribed(queue, () -> messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/rooms", Map.of("probe", true)));

        // when
        chatService.sendMessage(roomId, senderId, false, text("안녕하세요"));

        // then
        Map<String, Object> event = topic.await(p -> "MESSAGE_CREATED".equals(p.get("kind")));
        assertThat(event).containsEntry("roomId", roomId.toString());
        assertThat((Map<String, Object>) event.get("payload")).containsEntry("content", "안녕하세요");

        Map<String, Object> preview = queue.await(p -> p.containsKey("unreadCount"));
        assertThat(preview).containsEntry("roomId", roomId.toString()).containsEntry("unreadCount", 1);
    }

    @Test
    @DisplayName("링크가 든 메시지는 커밋 후 비동기로 미리보기가 저장되고 MESSAGE_UPDATED(linkPreview)로 전파된다")
    @SuppressWarnings("unchecked")
    void linkPreview_attachedAsync_broadcastsMessageUpdated() throws Exception {
        // given: 캐시 적중으로 외부 요청 없이 배선(AFTER_COMMIT → @Async → 저장 → 브로드캐스트)만 검증한다
        String url = "https://example.com/menu-" + UUID.randomUUID();
        cacheManager.getCache(CacheConfig.LINK_PREVIEW_CACHE)
                .put(url, new LinkPreview(url, "오늘의 메뉴", "설명", "https://example.com/a.png"));
        StompSession session = connect(memberId, new ErrorCollector());
        PayloadCollector topic = new PayloadCollector();
        session.subscribe(roomTopic(), topic);
        awaitSubscribed(topic, () -> messagingTemplate.convertAndSend(roomTopic(), Map.of("probe", true)));

        // when
        UUID messageId = chatService.sendMessage(roomId, senderId, false, text("여기 어때요 " + url + " !")).getId();

        // then
        Map<String, Object> event = topic.await(p -> "MESSAGE_UPDATED".equals(p.get("kind")));
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload).containsEntry("id", messageId.toString());
        assertThat((Map<String, Object>) payload.get("linkPreview"))
                .containsEntry("url", url).containsEntry("title", "오늘의 메뉴");
        assertThat(chatMessageRepository.findById(messageId).orElseThrow().getLinkPreview()).containsEntry("title", "오늘의 메뉴");
    }

    private StompSession connect(UUID userId, StompSessionHandlerAdapter handler) throws Exception {
        return connectWith(jwtTokenProvider.generateChatSocketToken(principal(userId)), handler);
    }

    private StompSession connectWith(String socketToken, StompSessionHandlerAdapter handler) throws Exception {
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), bearer(socketToken), handler)
                .get(5, SECONDS);
        sessions.add(session);
        return session;
    }

    private StompHeaders bearer(String token) {
        StompHeaders headers = new StompHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private UserPrincipal principal(UUID userId) {
        return new UserPrincipal(userId, userId + "@example.com", false);
    }

    // SUBSCRIBE는 브로커에 비동기로 등록되므로, 프로브가 도착할 때까지 다시 보낸다.
    private void awaitSubscribed(PayloadCollector collector, Runnable sendProbe) {
        await().atMost(5, SECONDS).until(() -> {
            sendProbe.run();
            return collector.payloads.poll(100, MILLISECONDS) != null;
        });
    }

    private String url() {
        return "ws://localhost:" + port + "/ws-chat";
    }

    private String roomTopic() {
        return "/topic/rooms/" + roomId;
    }

    private UUID saveUser(String nickname) {
        String unique = nickname + UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email(unique + "@example.com")
                .password("encoded")
                .name(nickname)
                .gender(Gender.FEMALE)
                .age(30)
                .nickname(unique)
                .build();
        return userRepository.save(user).getId();
    }

    private ChatMessageSendRequest text(String content) {
        ChatMessageSendRequest request = new ChatMessageSendRequest();
        ReflectionTestUtils.setField(request, "type", ChatMessageType.TEXT);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }

    /** 세션 핸들러: ERROR 프레임의 message 헤더(ErrorCode 이름)를 모은다. */
    private static class ErrorCollector extends StompSessionHandlerAdapter {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            messages.add(String.valueOf(headers.getFirst("message")));
        }
    }

    /** 구독 핸들러: JSON 페이로드를 Map으로 모은다. */
    private static class PayloadCollector implements StompFrameHandler {
        private final BlockingQueue<Map<String, Object>> payloads = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            payloads.add((Map<String, Object>) payload);
        }

        // 남은 프로브 등은 건너뛰고 조건에 맞는 첫 페이로드
        Map<String, Object> await(Predicate<Map<String, Object>> match) throws InterruptedException {
            long deadline = System.nanoTime() + SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                Map<String, Object> payload = payloads.poll(100, MILLISECONDS);
                if (payload != null && match.test(payload)) {
                    return payload;
                }
            }
            throw new AssertionError("5초 안에 기대한 STOMP 페이로드가 오지 않았습니다");
        }
    }
}
