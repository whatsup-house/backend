package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageSendRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageUpdateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatReportCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatImageUploadResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomDetailResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomIdResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatReaction;
import com.whatsuphouse.backend.domain.chat.entity.ChatReport;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.event.ChatMemberChangedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageDeletedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageReadEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageUpdatedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatReactionChangedEvent;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMuteRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatReactionRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatReportRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 100;
    // 업로드 API가 발급한 {userId}/{uuid}.{ext} 형태만 IMAGE 본문으로 허용(남의 경로로 서명 URL을 받아가는 것 차단)
    private static final Pattern IMAGE_PATH = Pattern.compile("[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReactionRepository chatReactionRepository;
    private final ChatMuteRepository chatMuteRepository;
    private final ChatReportRepository chatReportRepository;
    private final ChatAccessPolicy accessPolicy;
    private final ChatCipher chatCipher;
    private final ChatResponseAssembler assembler;
    private final UserService userService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ChatRoomSummaryResponse> listRooms(UUID userId, boolean isAdmin) {
        // 정지·탈퇴 사용자는 방 목록에서 제외(설계 8절)
        if (!isActiveAccount(userId)) {
            return List.of();
        }
        return assembler.toSummaries(chatRoomRepository.findVisibleRooms(userId), userId, isAdmin);
    }

    public ChatRoomIdResponse openInquiryRoom(UUID userId, boolean isAdmin) {
        accessPolicy.checkOpenInquiry(isAdmin);
        ChatRoom room = chatRoomRepository.findByInquiryUserId(userId)
                .orElseGet(() -> createInquiryRoom(userId));
        chatMemberRepository.findByRoomIdAndUserId(room.getId(), userId).ifPresent(ChatMember::unhide);
        return new ChatRoomIdResponse(room.getId());
    }

    // ponytail: 최초 동시 요청은 inquiry_user_id UNIQUE 위반으로 한쪽만 실패(재요청 시 기존 방 반환). 단일성은 DB가 보장.
    private ChatRoom createInquiryRoom(UUID userId) {
        ChatRoom room = chatRoomRepository.save(ChatRoom.inquiry(userId));
        // 문의방 멤버 = 문의 사용자 + 관리자 전원. 이후 생긴 관리자는 관리자 방 목록 조회 시 자동 등록된다.
        List<ChatMember> members = new ArrayList<>();
        members.add(ChatMember.join(room.getId(), userId, null));
        userService.listActiveAdminIds().stream()
                .filter(adminId -> !adminId.equals(userId))
                .forEach(adminId -> members.add(ChatMember.join(room.getId(), adminId, null)));
        chatMemberRepository.saveAll(members);
        return room;
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse getRoom(UUID roomId, UUID userId, boolean isAdmin) {
        ChatRoom room = findRoom(roomId);
        ChatMember me = findMemberOrNull(roomId, userId);
        accessPolicy.checkMember(me);
        boolean muted = chatMuteRepository.existsById(userId);
        ChatRoomDetailResponse.Permissions permissions = ChatRoomDetailResponse.Permissions.builder()
                .canSend(accessPolicy.canSend(me, muted, isActiveAccount(userId)))
                .isMuted(muted)
                .canLeave(accessPolicy.canLeave(room))
                .canHide(accessPolicy.canHide(room, isAdmin))
                .isAdmin(isAdmin)
                .build();
        return assembler.toDetail(room, userId, isAdmin, permissions);
    }

    /** before/after는 메시지 ID 커서. 둘 다 없으면 최신 size건. 결과는 항상 오래된 순. */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID roomId, UUID userId, boolean isAdmin,
                                                  UUID before, UUID after, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_PAGE_SIZE);
        }
        ChatRoom room = findRoom(roomId);
        accessPolicy.checkMember(findMemberOrNull(roomId, userId));

        // 재초대 멤버도 전체 기록을 본다: joined_at 필터 없음
        Pageable page = PageRequest.of(0, size);
        List<ChatMessage> messages;
        if (after != null) {
            ChatMessage cursor = findRoomMessage(roomId, after);
            messages = chatMessageRepository.findAfter(roomId, cursor.getCreatedAt(), cursor.getId(), page);
        } else {
            List<ChatMessage> newestFirst;
            if (before != null) {
                ChatMessage cursor = findRoomMessage(roomId, before);
                newestFirst = chatMessageRepository.findBefore(roomId, cursor.getCreatedAt(), cursor.getId(), page);
            } else {
                newestFirst = chatMessageRepository.findAllByRoomIdOrderByCreatedAtDescIdDesc(roomId, page);
            }
            messages = new ArrayList<>(newestFirst);
            Collections.reverse(messages);
        }
        return assembler.toMessages(room, messages, userId, isAdmin);
    }

    public ChatMessageResponse sendMessage(UUID roomId, UUID userId, boolean isAdmin, ChatMessageSendRequest request) {
        ChatRoom room = findRoom(roomId);
        ChatMember me = findMemberOrNull(roomId, userId);
        accessPolicy.checkSend(me, chatMuteRepository.existsById(userId), isActiveAccount(userId));
        validateContent(request.getType(), request.getContent(), userId);

        ChatCipher.Encrypted encrypted = chatCipher.encrypt(roomId, request.getContent());
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.user(roomId, userId, request.getType(), encrypted.ciphertext(), encrypted.nonce()));
        me.markRead(message.getId());
        if (room.isInquiry()) {
            // 숨긴 문의방도 새 메시지가 오면 목록에 다시 보인다
            chatMemberRepository.findByRoomIdAndUserId(roomId, room.getInquiryUserId()).ifPresent(ChatMember::unhide);
        }
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(roomId, message.getId()));
        return assembler.toMessages(room, List.of(message), userId, isAdmin).get(0);
    }

    public ChatMessageResponse updateMessage(UUID messageId, UUID userId, boolean isAdmin, ChatMessageUpdateRequest request) {
        ChatMessage message = findActiveMessage(messageId);
        ChatRoom room = findRoom(message.getRoomId());
        accessPolicy.checkEdit(findMemberOrNull(room.getId(), userId), message, userId, chatMuteRepository.existsById(userId));

        ChatCipher.Encrypted encrypted = chatCipher.encrypt(room.getId(), request.getContent());
        message.edit(encrypted.ciphertext(), encrypted.nonce());
        eventPublisher.publishEvent(new ChatMessageUpdatedEvent(room.getId(), messageId));
        return assembler.toMessages(room, List.of(message), userId, isAdmin).get(0);
    }

    public void deleteMessage(UUID messageId, UUID userId, boolean isAdmin) {
        ChatMessage message = findActiveMessage(messageId);
        findRoom(message.getRoomId());
        accessPolicy.checkDelete(findMemberOrNull(message.getRoomId(), userId), message, userId, isAdmin);
        message.delete();
        eventPublisher.publishEvent(new ChatMessageDeletedEvent(message.getRoomId(), messageId));
    }

    public List<ChatMessageResponse.Reaction> toggleReaction(UUID messageId, String emoji, UUID userId) {
        if (!ChatReaction.ALLOWED_EMOJIS.contains(emoji)) {
            throw new CustomException(ErrorCode.CHAT_INVALID_EMOJI);
        }
        ChatMessage message = findActiveMessage(messageId);
        findRoom(message.getRoomId());
        accessPolicy.checkMember(findMemberOrNull(message.getRoomId(), userId));

        chatReactionRepository.findById(new ChatReaction.Key(messageId, userId, emoji))
                .ifPresentOrElse(chatReactionRepository::delete,
                        () -> chatReactionRepository.save(ChatReaction.of(messageId, userId, emoji)));
        eventPublisher.publishEvent(new ChatReactionChangedEvent(message.getRoomId(), messageId));
        return assembler.toReactions(messageId, userId);
    }

    public void reportMessage(UUID messageId, UUID userId, boolean isAdmin, ChatReportCreateRequest request) {
        ChatMessage message = findActiveMessage(messageId);
        findRoom(message.getRoomId());
        accessPolicy.checkReport(findMemberOrNull(message.getRoomId(), userId), isAdmin);
        chatReportRepository.save(ChatReport.of(messageId, userId, request.getReason()));
    }

    /** 조용히 나가기(GROUP만). 시스템 메시지를 남기지 않는다. */
    public void leaveRoom(UUID roomId, UUID userId) {
        ChatRoom room = findRoom(roomId);
        ChatMember me = findMemberOrNull(roomId, userId);
        accessPolicy.checkMember(me);
        accessPolicy.checkLeave(room);
        me.leave();
        eventPublisher.publishEvent(new ChatMemberChangedEvent(roomId, List.of(userId)));
    }

    /** 문의방 목록 숨기기(INQUIRY, 사용자만) */
    public void hideRoom(UUID roomId, UUID userId, boolean isAdmin) {
        ChatRoom room = findRoom(roomId);
        ChatMember me = findMemberOrNull(roomId, userId);
        accessPolicy.checkMember(me);
        accessPolicy.checkHide(room, isAdmin);
        me.hide();
    }

    // ponytail: 업로드만 하고 전송하지 않은 이미지는 남는다. 쌓이면 chat-images 정리 스케줄러 추가.
    public ChatImageUploadResponse uploadImage(MultipartFile file, UUID userId) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new CustomException(ErrorCode.CHAT_IMAGE_TOO_LARGE);
        }
        String path = storageService.uploadPrivate(file, ChatResponseAssembler.IMAGE_BUCKET, userId.toString());
        String url = storageService.createSignedUrls(ChatResponseAssembler.IMAGE_BUCKET, List.of(path),
                ChatResponseAssembler.SIGNED_URL_SECONDS).get(path);
        return new ChatImageUploadResponse(path, url);
    }

    /** 읽음 처리. 실시간(STOMP) 일감이 /app/rooms/{id}/read 에서 호출한다. 더 오래된 메시지로의 역행은 무시. */
    public void markRead(UUID roomId, UUID userId, UUID messageId) {
        findRoom(roomId);
        ChatMember me = findMemberOrNull(roomId, userId);
        accessPolicy.checkMember(me);
        ChatMessage target = findRoomMessage(roomId, messageId);
        if (me.getLastReadMessageId() != null) {
            boolean isBackward = chatMessageRepository.findById(me.getLastReadMessageId())
                    .map(current -> !target.getCreatedAt().isAfter(current.getCreatedAt()))
                    .orElse(false);
            if (isBackward) {
                return;
            }
        }
        me.markRead(messageId);
        eventPublisher.publishEvent(new ChatMessageReadEvent(roomId, userId, messageId));
    }

    /** 링크 미리보기용 TEXT 본문 평문. 삭제됐거나 TEXT가 아니면 empty. */
    @Transactional(readOnly = true)
    public Optional<String> getMessageText(UUID messageId) {
        return chatMessageRepository.findByIdAndDeletedAtIsNull(messageId)
                .filter(m -> m.getType() == ChatMessageType.TEXT)
                .map(assembler::decrypt);
    }

    /** 링크 미리보기 저장 후 MESSAGE_UPDATED 전파. 조회 중 삭제된 메시지는 건너뛴다. */
    public void attachLinkPreview(UUID messageId, Map<String, Object> linkPreview) {
        chatMessageRepository.findByIdAndDeletedAtIsNull(messageId).ifPresent(message -> {
            message.attachLinkPreview(linkPreview);
            eventPublisher.publishEvent(new ChatMessageUpdatedEvent(message.getRoomId(), messageId));
        });
    }

    /** STOMP CONNECT: 정지·탈퇴 계정은 소켓 연결을 거부한다(설계 8절). */
    @Transactional(readOnly = true)
    public void checkConnectable(UUID userId) {
        if (!isActiveAccount(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** STOMP SUBSCRIBE /topic/rooms/{roomId}: 참여 중(left_at NULL) 멤버만. */
    @Transactional(readOnly = true)
    public void checkSubscribe(UUID roomId, UUID userId) {
        findRoom(roomId);
        accessPolicy.checkMember(findMemberOrNull(roomId, userId));
    }

    private void validateContent(ChatMessageType type, String content, UUID userId) {
        if (type == ChatMessageType.SYSTEM) {
            throw new CustomException(ErrorCode.CHAT_INVALID_MESSAGE);
        }
        if (type == ChatMessageType.IMAGE
                && !(content.startsWith(userId + "/") && IMAGE_PATH.matcher(content).matches())) {
            throw new CustomException(ErrorCode.CHAT_INVALID_MESSAGE);
        }
    }

    private boolean isActiveAccount(UUID userId) {
        User user = userService.findUsersByIds(List.of(userId)).get(userId);
        return user != null && !user.isWithdrawn() && !user.isAccountSuspended();
    }

    private ChatRoom findRoom(UUID roomId) {
        return chatRoomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    // 멤버십 판정은 ChatAccessPolicy가 한다(없으면 null을 넘겨 CHAT_NOT_MEMBER).
    private ChatMember findMemberOrNull(UUID roomId, UUID userId) {
        return chatMemberRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
    }

    private ChatMessage findActiveMessage(UUID messageId) {
        return chatMessageRepository.findByIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

    private ChatMessage findRoomMessage(UUID roomId, UUID messageId) {
        return chatMessageRepository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }
}
