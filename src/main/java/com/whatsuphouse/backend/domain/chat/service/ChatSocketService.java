package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.dto.response.ChatLastMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomPreviewResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatSocketEventResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatSocketEventResponse.Kind;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.chat.socket.ChatPresenceRegistry;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * STOMP 실시간 전송(docs/chat-design.md 3절). 코어 이벤트 리스너(AFTER_COMMIT)가 호출한다.
 * /topic/rooms/{roomId} 는 방 전체에 한 번 보내므로 페이로드는 뷰어 중립으로 조립한다(ChatSocketEventResponse 참고).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatSocketService {

    public static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    public static final String ROOMS_QUEUE = "/queue/rooms";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatResponseAssembler assembler;
    private final ChatPresenceRegistry presenceRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    /** /topic/rooms/{uuid}(정규 표기만) → roomId. 와일드카드·하위 경로·다른 목적지는 empty. */
    public static Optional<UUID> parseRoomId(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return Optional.empty();
        }
        String raw = destination.substring(ROOM_TOPIC_PREFIX.length());
        try {
            UUID roomId = UUID.fromString(raw);
            return roomId.toString().equals(raw) ? Optional.of(roomId) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 메시지 생성·수정·삭제·리액션. 생성이면 멤버 각자의 /user/queue/rooms 에 방 목록 갱신도 보낸다. */
    public void broadcastMessage(Kind kind, UUID roomId, UUID messageId) {
        ChatRoom room = findRoom(roomId);
        ChatMessage message = findMessage(messageId);
        send(roomId, kind, toNeutralMessage(room, message));
        if (kind == Kind.MESSAGE_CREATED) {
            sendRoomPreviews(room, message);
        }
    }

    /** 공지 등록/교체는 공지 메시지, 해제는 null 페이로드. */
    public void broadcastNotice(UUID roomId, UUID noticeMessageId) {
        ChatRoom room = findRoom(roomId);
        send(roomId, Kind.NOTICE_CHANGED, noticeMessageId == null ? null : toNeutralMessage(room, findMessage(noticeMessageId)));
    }

    /** 대상자에게도 알린 뒤(내보내진 사람이 알 수 있게), 더는 멤버가 아닌 사용자의 방 구독을 브로커에서 떼어낸다. */
    public void broadcastMemberChanged(UUID roomId, List<UUID> userIds) {
        send(roomId, Kind.MEMBER_CHANGED, Map.of("userIds", userIds));
        Set<UUID> activeIds = chatMemberRepository.findAllByRoomIdAndUserIdIn(roomId, userIds).stream()
                .filter(ChatMember::isActive)
                .map(ChatMember::getUserId)
                .collect(Collectors.toSet());
        List<UUID> removedIds = userIds.stream().filter(id -> !activeIds.contains(id)).toList();
        presenceRegistry.revoke(roomId, removedIds).forEach(this::unsubscribe);
    }

    public void broadcastRead(UUID roomId, UUID userId, UUID messageId) {
        send(roomId, Kind.READ, Map.of("userId", userId, "messageId", messageId));
    }

    // 뷰어 중립: 비관리자 시점 표시명(문의방 관리자 = 와썹하우스), viewerId 없음(isMine=false)
    private ChatMessageResponse toNeutralMessage(ChatRoom room, ChatMessage message) {
        return assembler.toMessages(room, List.of(message), null, false).get(0);
    }

    private void sendRoomPreviews(ChatRoom room, ChatMessage message) {
        ChatLastMessageResponse lastMessage = assembler.toLastMessage(message);
        Map<UUID, Long> unreadCounts = chatMessageRepository.countUnreadByMember(room.getId(), ChatMessageType.SYSTEM).stream()
                .collect(Collectors.toMap(ChatMessageRepository.UserCount::getUserId, ChatMessageRepository.UserCount::getCount));
        for (ChatMember member : chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(room.getId())) {
            messagingTemplate.convertAndSendToUser(member.getUserId().toString(), ROOMS_QUEUE, new ChatRoomPreviewResponse(
                    room.getId(), lastMessage, unreadCounts.getOrDefault(member.getUserId(), 0L).intValue()));
        }
    }

    private void send(UUID roomId, Kind kind, Object payload) {
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomId, new ChatSocketEventResponse(kind, roomId, payload));
    }

    // 클라이언트 UNSUBSCRIBE와 같은 메시지를 브로커 채널에 넣어 서버 쪽에서 구독을 해제한다.
    private void unsubscribe(ChatPresenceRegistry.RoomSubscription subscription) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        headers.setSessionId(subscription.sessionId());
        headers.setSubscriptionId(subscription.subscriptionId());
        messagingTemplate.getMessageChannel().send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
    }

    private ChatRoom findRoom(UUID roomId) {
        return chatRoomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private ChatMessage findMessage(UUID messageId) {
        return chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }
}
