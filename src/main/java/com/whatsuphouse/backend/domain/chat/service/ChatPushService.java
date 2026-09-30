package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.dto.request.PushSubscriptionCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.PushPublicKeyResponse;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.entity.PushSubscription;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.chat.repository.PushSubscriptionRepository;
import com.whatsuphouse.backend.domain.chat.socket.ChatPresenceRegistry;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 채팅 웹 푸시(docs/chat-design.md 7절). 구독 저장·삭제와, 새 메시지를 앱을 보고 있지 않은 방 멤버에게 알리는 발송.
 * 발송 대상: 참여 중(left_at NULL, hidden 무관) 멤버 − 발신자 − 이 방을 소켓으로 구독 중인 사용자 − 정지·탈퇴 계정. SYSTEM 메시지는 보내지 않는다.
 * 클래스 단위 @Transactional을 두지 않는다: 발송 리스너가 외부 HTTP를 기다리는 동안 트랜잭션을 잡지 않게.
 */
@Service
@RequiredArgsConstructor
public class ChatPushService {

    static final int MAX_BODY_LENGTH = 100;
    static final String IMAGE_BODY = "사진을 보냈습니다.";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatPresenceRegistry presenceRegistry;
    private final ChatResponseAssembler assembler;
    private final UserService userService;
    private final WebPushSender webPushSender;

    /** 서비스워커가 받는 JSON. 클릭 시 url로 이동. */
    public record Payload(String title, String body, String url) {
    }

    public PushPublicKeyResponse getPublicKey() {
        checkEnabled();
        return new PushPublicKeyResponse(webPushSender.getPublicKey());
    }

    /** 같은 endpoint 재등록은 키·소유자 갱신. */
    // ponytail: 같은 endpoint 최초 동시 등록은 UNIQUE 위반으로 한쪽만 실패(재시도 시 갱신). 단일성은 DB가 보장.
    @Transactional
    public void registerSubscription(UUID userId, PushSubscriptionCreateRequest request) {
        checkEnabled();
        String endpoint = request.getEndpoint();
        validateEndpoint(endpoint);
        PushSubscriptionCreateRequest.Keys keys = request.getKeys();
        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                subscription -> subscription.renew(userId, keys.getP256dh(), keys.getAuth()),
                () -> pushSubscriptionRepository.save(PushSubscription.of(userId, endpoint, keys.getP256dh(), keys.getAuth())));
    }

    /** 본인 구독만 지운다. 없으면 아무 일도 하지 않는다. */
    @Transactional
    public void deleteSubscription(UUID userId, String endpoint) {
        checkEnabled();
        pushSubscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
    }

    @Async("chatPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        if (!webPushSender.isEnabled()) {
            return;
        }
        Optional<ChatMessage> found = chatMessageRepository.findByIdAndDeletedAtIsNull(event.getMessageId())
                .filter(m -> m.getType() != ChatMessageType.SYSTEM);
        Optional<ChatRoom> room = chatRoomRepository.findByIdAndDeletedAtIsNull(event.getRoomId());
        if (found.isEmpty() || room.isEmpty()) {
            return;
        }
        ChatMessage message = found.get();
        Set<UUID> watching = presenceRegistry.listSubscriberIds(event.getRoomId());
        List<UUID> recipientIds = chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(event.getRoomId()).stream()
                .map(ChatMember::getUserId)
                .filter(id -> !id.equals(message.getSenderId()) && !watching.contains(id))
                .toList();
        List<PushSubscription> subscriptions = recipientIds.isEmpty() ? List.of()
                : pushSubscriptionRepository.findAllByUserIdIn(recipientIds);
        if (subscriptions.isEmpty()) {
            return;
        }
        send(room.get(), message, subscriptions);
    }

    private void send(ChatRoom room, ChatMessage message, List<PushSubscription> subscriptions) {
        Set<UUID> userIds = new HashSet<>();
        subscriptions.forEach(s -> userIds.add(s.getUserId()));
        userIds.add(message.getSenderId());
        if (room.isInquiry()) {
            userIds.add(room.getInquiryUserId());
        }
        Map<UUID, User> users = userService.findUsersByIds(userIds);
        String preview = preview(message);
        // 제목·발신자 표시명은 뷰어가 관리자인지에 따라서만 달라진다(문의방 관리자 = 와썹하우스).
        Map<Boolean, Payload> payloads = new HashMap<>();
        for (PushSubscription subscription : subscriptions) {
            User recipient = users.get(subscription.getUserId());
            if (recipient == null || recipient.isWithdrawn() || recipient.isAccountSuspended()) {
                continue;
            }
            Payload payload = payloads.computeIfAbsent(recipient.isAdmin(),
                    viewerIsAdmin -> toPayload(room, message, preview, users, viewerIsAdmin));
            if (webPushSender.send(subscription, payload)) {
                pushSubscriptionRepository.delete(subscription);
            }
        }
    }

    // 문의방: 제목이 곧 상대(와썹하우스 / 문의자)라 본문만. 단체방: 제목은 방 이름, 본문은 "닉네임: 내용".
    private Payload toPayload(ChatRoom room, ChatMessage message, String preview, Map<UUID, User> users, boolean viewerIsAdmin) {
        String title = Objects.requireNonNullElse(assembler.roomName(room, users, viewerIsAdmin),
                ChatResponseAssembler.INQUIRY_DISPLAY_NAME);
        String senderName = assembler.displayNickname(users.get(message.getSenderId()), room, viewerIsAdmin);
        String body = room.isInquiry() || senderName == null ? preview : senderName + ": " + preview;
        return new Payload(title, body, "/chat/" + room.getId());
    }

    private String preview(ChatMessage message) {
        if (message.getType() == ChatMessageType.IMAGE) {
            return IMAGE_BODY;
        }
        String text = assembler.decrypt(message);
        // 코드포인트 단위로 잘라 이모지(서로게이트 쌍)가 반으로 쪼개지지 않게 한다.
        return text.codePointCount(0, text.length()) > MAX_BODY_LENGTH
                ? text.substring(0, text.offsetByCodePoints(0, MAX_BODY_LENGTH)) + "…"
                : text;
    }

    private void checkEnabled() {
        if (!webPushSender.isEnabled()) {
            throw new CustomException(ErrorCode.CHAT_PUSH_DISABLED);
        }
    }

    // 서버가 이 주소로 POST하므로 링크 미리보기와 같은 사설망 차단(SSRF)을 건다.
    // ponytail: 등록 시점 DNS 기준. 발송 시 재해석되는 DNS 재바인딩 창은 LinkPreviewFetcher와 같은 한계.
    private void validateEndpoint(String endpoint) {
        String host;
        try {
            host = URI.create(endpoint).getHost();
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.CHAT_PUSH_INVALID_ENDPOINT);
        }
        if (host == null || !LinkPreviewFetcher.isPublicHost(host)) {
            throw new CustomException(ErrorCode.CHAT_PUSH_INVALID_ENDPOINT);
        }
    }
}
