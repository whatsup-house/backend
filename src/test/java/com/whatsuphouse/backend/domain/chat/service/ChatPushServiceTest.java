package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.entity.PushSubscription;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.chat.repository.PushSubscriptionRepository;
import com.whatsuphouse.backend.domain.chat.socket.ChatPresenceRegistry;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 발송 대상 선정(멤버십·소켓 구독·발신자·SYSTEM)을 실제 JPA 조회로 검증한다. 실제 HTTP 발송(WebPushSender)과 소켓 현황은 목. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestJpaConfig.class, ChatPushService.class, ChatResponseAssembler.class, ChatCipher.class})
@ActiveProfiles("test")
class ChatPushServiceTest {

    @Autowired
    private ChatPushService chatPushService;

    @Autowired
    private ChatCipher chatCipher;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @MockitoBean
    private WebPushSender webPushSender;

    @MockitoBean
    private ChatPresenceRegistry presenceRegistry;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private StorageService storageService;

    private final Map<UUID, User> users = new HashMap<>();
    private UUID roomId;
    private UUID senderId;
    private UUID offlineId;
    private UUID hiderId;

    @BeforeEach
    void setUp() {
        UUID adminId = addUser("운영자", true);
        senderId = addUser("보낸이", false);
        UUID watcherId = addUser("보는중", false);
        offlineId = addUser("오프라인", false);
        hiderId = addUser("숨김", false);
        UUID leaverId = addUser("나감", false);

        roomId = chatRoomRepository.save(ChatRoom.group("금요 모임", null, null, adminId)).getId();
        for (UUID id : new UUID[]{senderId, watcherId, offlineId, hiderId, leaverId}) {
            ChatMember member = chatMemberRepository.save(ChatMember.join(roomId, id, null));
            if (id.equals(hiderId)) {
                member.hide();
            }
            if (id.equals(leaverId)) {
                member.leave();
            }
            pushSubscriptionRepository.save(PushSubscription.of(id, endpoint(id), "p256dh", "auth"));
        }

        given(userService.findUsersByIds(anyCollection())).willAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return users.entrySet().stream().filter(e -> ids.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
        given(webPushSender.isEnabled()).willReturn(true);
        given(presenceRegistry.listSubscriberIds(roomId)).willReturn(Set.of(watcherId));
        // 숨김 멤버의 구독은 푸시 서비스가 410(만료)을 돌려준다고 가정
        given(webPushSender.send(argThat(s -> s != null && s.getUserId().equals(hiderId)), any())).willReturn(true);
    }

    @Test
    @DisplayName("소켓으로 방을 보고 있는 멤버·발신자·나간 멤버에겐 보내지 않고, 숨긴 멤버에겐 보낸다. 만료 응답 구독은 지운다")
    void onMessageCreated_sendsOnlyToMembersNotWatching() {
        // given
        ChatCipher.Encrypted encrypted = chatCipher.encrypt(roomId, "안녕하세요");
        UUID messageId = chatMessageRepository.save(ChatMessage.user(
                roomId, senderId, ChatMessageType.TEXT, encrypted.ciphertext(), encrypted.nonce())).getId();

        // when
        chatPushService.onMessageCreated(new ChatMessageCreatedEvent(roomId, messageId));

        // then
        ArgumentCaptor<PushSubscription> subscriptions = ArgumentCaptor.forClass(PushSubscription.class);
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(webPushSender, times(2)).send(subscriptions.capture(), payloads.capture());
        assertThat(subscriptions.getAllValues()).extracting(PushSubscription::getUserId)
                .containsExactlyInAnyOrder(offlineId, hiderId);
        assertThat(payloads.getValue())
                .isEqualTo(new ChatPushService.Payload("금요 모임", "보낸이: 안녕하세요", "/chat/" + roomId));
        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint(hiderId))).isEmpty();
        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint(offlineId))).isPresent();
    }

    @Test
    @DisplayName("SYSTEM 메시지는 푸시하지 않는다")
    void onMessageCreated_skipsSystemMessage() {
        // given
        UUID messageId = chatMessageRepository.save(
                ChatMessage.system(roomId, ChatSystemKind.JOINED, Map.of("nicknames", "새멤버"))).getId();

        // when
        chatPushService.onMessageCreated(new ChatMessageCreatedEvent(roomId, messageId));

        // then
        verify(webPushSender, never()).send(any(), any());
    }

    private String endpoint(UUID userId) {
        return "https://push.example.com/" + userId;
    }

    private UUID addUser(String nickname, boolean isAdmin) {
        User user = User.builder()
                .email(nickname + "@example.com")
                .password("encoded")
                .name(nickname)
                .gender(Gender.FEMALE)
                .age(30)
                .nickname(nickname)
                .build();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "isAdmin", isAdmin);
        users.put(id, user);
        return id;
    }
}
