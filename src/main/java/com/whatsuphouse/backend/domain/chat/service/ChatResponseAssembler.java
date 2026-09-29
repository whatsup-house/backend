package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.dto.response.ChatLastMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatReportResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomDetailResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatReaction;
import com.whatsuphouse.backend.domain.chat.entity.ChatReport;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatReactionRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 채팅 응답 조립(복호화·표시명·리액션·안읽은 수·서명 URL). 복호화는 여기서만 한다.
 * 표시명 규칙: 문의방의 관리자는 비관리자 뷰어에게 "와썹하우스", 탈퇴 회원은 null(FE가 "(탈퇴한 회원)").
 */
@Component
@RequiredArgsConstructor
public class ChatResponseAssembler {

    public static final String INQUIRY_DISPLAY_NAME = "와썹하우스";
    public static final String IMAGE_BUCKET = "chat-images";
    public static final int SIGNED_URL_SECONDS = 3600;

    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReactionRepository chatReactionRepository;
    private final ChatCipher chatCipher;
    private final UserService userService;
    private final StorageService storageService;

    // 안 읽은 사람 수 계산 단위. 문의방은 관리자 전원을 "와썹하우스" 한 명으로 센다.
    private record Reader(Set<UUID> userIds, LocalDateTime readAt) {
    }

    public List<ChatMessageResponse> toMessages(ChatRoom room, List<ChatMessage> messages, UUID viewerId, boolean viewerIsAdmin) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> senders = userService.findUsersByIds(messages.stream()
                .map(ChatMessage::getSenderId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, List<ChatReaction>> reactions = chatReactionRepository
                .findAllByMessageIdIn(messages.stream().map(ChatMessage::getId).toList()).stream()
                .collect(Collectors.groupingBy(ChatReaction::getMessageId));
        List<Reader> readers = readers(room);

        Map<UUID, String> plaintexts = new HashMap<>();
        messages.stream()
                .filter(m -> !m.isDeleted() && m.getType() != ChatMessageType.SYSTEM)
                .forEach(m -> plaintexts.put(m.getId(), decrypt(m)));
        Map<String, String> imageUrls = storageService.createSignedUrls(IMAGE_BUCKET, messages.stream()
                .filter(m -> m.getType() == ChatMessageType.IMAGE && plaintexts.containsKey(m.getId()))
                .map(m -> plaintexts.get(m.getId()))
                .toList(), SIGNED_URL_SECONDS);

        return messages.stream().map(m -> {
            String plaintext = plaintexts.get(m.getId());
            return ChatMessageResponse.builder()
                    .id(m.getId())
                    .roomId(m.getRoomId())
                    .type(m.getType())
                    .sender(toSender(m.getSenderId(), senders, room, viewerIsAdmin))
                    .content(m.getType() == ChatMessageType.TEXT ? plaintext : null)
                    .imageUrl(m.getType() == ChatMessageType.IMAGE && plaintext != null ? imageUrls.get(plaintext) : null)
                    .systemKind(m.getSystemKind())
                    .systemParams(m.getSystemParams())
                    .linkPreview(m.isDeleted() ? null : m.getLinkPreview())
                    .reactions(toReactions(reactions.getOrDefault(m.getId(), List.of()), viewerId))
                    .unreadCount(m.getType() == ChatMessageType.SYSTEM ? 0 : unreadCount(m, readers))
                    .isEdited(m.getEditedAt() != null)
                    .isDeleted(m.isDeleted())
                    .createdAt(m.getCreatedAt())
                    .build();
        }).toList();
    }

    public List<ChatMessageResponse.Reaction> toReactions(UUID messageId, UUID viewerId) {
        return toReactions(chatReactionRepository.findAllByMessageIdIn(List.of(messageId)), viewerId);
    }

    public List<ChatRoomSummaryResponse> toSummaries(List<ChatRoom> rooms, UUID viewerId, boolean viewerIsAdmin) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<UUID> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<UUID, Long> memberCounts = toCountMap(chatMemberRepository.countActiveByRoomIds(roomIds));
        Map<UUID, Long> unreadCounts = toCountMap(chatMessageRepository.countUnread(viewerId, roomIds, ChatMessageType.SYSTEM));
        Map<UUID, ChatMessage> lastMessages = chatMessageRepository.findLastMessages(roomIds).stream()
                .collect(Collectors.toMap(ChatMessage::getRoomId, Function.identity(), (a, b) -> a));
        Set<UUID> joinedRoomIds = chatMemberRepository.findAllByUserIdAndLeftAtIsNull(viewerId).stream()
                .map(ChatMember::getRoomId).collect(Collectors.toSet());
        Map<UUID, User> inquiryUsers = viewerIsAdmin
                ? userService.findUsersByIds(rooms.stream()
                        .map(ChatRoom::getInquiryUserId).filter(Objects::nonNull).collect(Collectors.toSet()))
                : Map.of();

        return rooms.stream()
                .sorted(Comparator.comparing((ChatRoom r) -> activityAt(r, lastMessages.get(r.getId()))).reversed())
                .map(room -> {
                    ChatMessage last = lastMessages.get(room.getId());
                    return ChatRoomSummaryResponse.builder()
                            .id(room.getId())
                            .type(room.getType())
                            .name(roomName(room, inquiryUsers, viewerIsAdmin))
                            .sourceType(room.getSourceType())
                            .sourceId(room.getSourceId())
                            .memberCount(memberCounts.getOrDefault(room.getId(), 0L).intValue())
                            .lastMessage(last == null ? null : toLastMessage(last))
                            .unreadCount(unreadCounts.getOrDefault(room.getId(), 0L).intValue())
                            .isUnanswered(room.isInquiry() && last != null
                                    && room.getInquiryUserId().equals(last.getSenderId()))
                            .isMember(joinedRoomIds.contains(room.getId()))
                            .build();
                })
                .toList();
    }

    public ChatRoomDetailResponse toDetail(ChatRoom room, UUID viewerId, boolean viewerIsAdmin,
                                           ChatRoomDetailResponse.Permissions permissions) {
        List<ChatMember> members = chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(room.getId());
        Map<UUID, User> users = userService.findUsersByIds(members.stream()
                .map(ChatMember::getUserId).collect(Collectors.toSet()));
        ChatMessageResponse notice = room.getNoticeMessageId() == null ? null
                : chatMessageRepository.findByIdAndDeletedAtIsNull(room.getNoticeMessageId())
                        .map(m -> toMessages(room, List.of(m), viewerId, viewerIsAdmin).get(0))
                        .orElse(null);

        return ChatRoomDetailResponse.builder()
                .id(room.getId())
                .type(room.getType())
                .name(roomName(room, users, viewerIsAdmin))
                .sourceType(room.getSourceType())
                .sourceId(room.getSourceId())
                .memberCount(members.size())
                .members(members.stream().map(m -> {
                    User user = users.get(m.getUserId());
                    return new ChatRoomDetailResponse.Member(
                            m.getUserId(), displayNickname(user, room, viewerIsAdmin), user != null && user.isAdmin());
                }).toList())
                .notice(notice)
                .permissions(permissions)
                .build();
    }

    /** 관리자 검토용: 실제 닉네임, 삭제된 메시지도 TEXT 원문을 보여준다. */
    public List<ChatReportResponse> toReports(List<ChatReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<UUID, ChatMessage> messages = chatMessageRepository
                .findAllById(reports.stream().map(ChatReport::getMessageId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        reports.forEach(r -> userIds.add(r.getReporterId()));
        messages.values().stream().map(ChatMessage::getSenderId).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, User> users = userService.findUsersByIds(userIds);

        return reports.stream().map(r -> {
            ChatMessage m = messages.get(r.getMessageId());
            return ChatReportResponse.builder()
                    .id(r.getId())
                    .roomId(m.getRoomId())
                    .messageId(m.getId())
                    .reporterId(r.getReporterId())
                    .reporterNickname(displayNickname(users.get(r.getReporterId()), null, true))
                    .reason(r.getReason())
                    .status(r.getStatus())
                    .createdAt(r.getCreatedAt())
                    .messageType(m.getType())
                    .messageSenderId(m.getSenderId())
                    .messageSenderNickname(m.getSenderId() == null ? null
                            : displayNickname(users.get(m.getSenderId()), null, true))
                    .messageContent(m.getType() == ChatMessageType.TEXT ? decrypt(m) : null)
                    .isMessageDeleted(m.isDeleted())
                    .build();
        }).toList();
    }

    public String displayNickname(User user, ChatRoom room, boolean viewerIsAdmin) {
        if (user == null) {
            return null;
        }
        if (user.isAdmin() && room != null && room.isInquiry() && !viewerIsAdmin) {
            return INQUIRY_DISPLAY_NAME;
        }
        return user.isWithdrawn() ? null : user.getNickname();
    }

    private String roomName(ChatRoom room, Map<UUID, User> users, boolean viewerIsAdmin) {
        if (!room.isInquiry()) {
            return room.getName();
        }
        return viewerIsAdmin ? displayNickname(users.get(room.getInquiryUserId()), room, true) : INQUIRY_DISPLAY_NAME;
    }

    private ChatMessageResponse.Sender toSender(UUID senderId, Map<UUID, User> users, ChatRoom room, boolean viewerIsAdmin) {
        if (senderId == null) {
            return null;
        }
        User user = users.get(senderId);
        return new ChatMessageResponse.Sender(senderId, displayNickname(user, room, viewerIsAdmin), user != null && user.isAdmin());
    }

    private List<ChatMessageResponse.Reaction> toReactions(List<ChatReaction> reactions, UUID viewerId) {
        return reactions.stream()
                .collect(Collectors.groupingBy(ChatReaction::getEmoji))
                .entrySet().stream()
                .sorted(Comparator.comparingInt(e -> ChatReaction.ALLOWED_EMOJIS.indexOf(e.getKey())))
                .map(e -> new ChatMessageResponse.Reaction(e.getKey(), e.getValue().size(),
                        e.getValue().stream().anyMatch(r -> r.getUserId().equals(viewerId))))
                .toList();
    }

    public ChatLastMessageResponse toLastMessage(ChatMessage m) {
        return ChatLastMessageResponse.builder()
                .id(m.getId())
                .type(m.getType())
                .senderId(m.getSenderId())
                .content(m.getType() == ChatMessageType.TEXT && !m.isDeleted() ? decrypt(m) : null)
                .systemKind(m.getSystemKind())
                .systemParams(m.getSystemParams())
                .isDeleted(m.isDeleted())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private List<Reader> readers(ChatRoom room) {
        List<ChatMemberRepository.ReadState> states = chatMemberRepository.findReadStates(room.getId());
        if (!room.isInquiry()) {
            return states.stream().map(s -> new Reader(Set.of(s.getUserId()), s.getReadAt())).toList();
        }
        List<Reader> readers = new ArrayList<>();
        Set<UUID> staffIds = new HashSet<>();
        LocalDateTime staffReadAt = null;
        for (ChatMemberRepository.ReadState s : states) {
            if (s.getUserId().equals(room.getInquiryUserId())) {
                readers.add(new Reader(Set.of(s.getUserId()), s.getReadAt()));
            } else {
                staffIds.add(s.getUserId());
                if (s.getReadAt() != null && (staffReadAt == null || s.getReadAt().isAfter(staffReadAt))) {
                    staffReadAt = s.getReadAt();
                }
            }
        }
        if (!staffIds.isEmpty()) {
            readers.add(new Reader(staffIds, staffReadAt));
        }
        return readers;
    }

    private int unreadCount(ChatMessage message, List<Reader> readers) {
        return (int) readers.stream()
                .filter(r -> !r.userIds().contains(message.getSenderId()))
                .filter(r -> r.readAt() == null || r.readAt().isBefore(message.getCreatedAt()))
                .count();
    }

    private LocalDateTime activityAt(ChatRoom room, ChatMessage last) {
        return last != null ? last.getCreatedAt() : room.getCreatedAt();
    }

    private Map<UUID, Long> toCountMap(Collection<ChatMessageRepository.RoomCount> counts) {
        return counts.stream().collect(Collectors.toMap(ChatMessageRepository.RoomCount::getRoomId,
                ChatMessageRepository.RoomCount::getCount));
    }

    // 응답 조립 외에 링크 미리보기(ChatService.getMessageText)도 이 경로로만 복호화한다.
    public String decrypt(ChatMessage m) {
        return chatCipher.decrypt(m.getRoomId(), m.getContentEnc(), m.getNonce());
    }
}
