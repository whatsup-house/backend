package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatGroupRoomCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMemberAddRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMuteRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatNoticeUpdateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatReportStatusRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatReportResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomIdResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatSourceMemberResponse;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.entity.ChatMute;
import com.whatsuphouse.backend.domain.chat.entity.ChatReport;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatReportStatus;
import com.whatsuphouse.backend.domain.chat.enums.ChatRoomType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import com.whatsuphouse.backend.domain.chat.event.ChatMemberChangedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.whatsuphouse.backend.domain.chat.event.ChatNoticeChangedEvent;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMessageRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatMuteRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatReportRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminChatService {

    private static final String NICKNAMES = "nicknames";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMuteRepository chatMuteRepository;
    private final ChatReportRepository chatReportRepository;
    private final ChatAccessPolicy accessPolicy;
    private final ChatResponseAssembler assembler;
    private final UserService userService;
    private final AdminApplicationService adminApplicationService;
    private final MatchingService matchingService;
    private final ApplicationEventPublisher eventPublisher;

    public ChatRoomIdResponse createGroupRoom(UUID adminId, boolean isAdmin, ChatGroupRoomCreateRequest request) {
        accessPolicy.checkAdmin(isAdmin);
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.group(request.getName(), request.getSourceType(), request.getSourceId(), adminId));
        chatMemberRepository.save(ChatMember.join(room.getId(), adminId, null));
        Set<UUID> invitees = new LinkedHashSet<>(request.getMemberIds());
        invitees.remove(adminId);
        joinMembers(room, invitees);
        return new ChatRoomIdResponse(room.getId());
    }

    /** 전체 방. 관리자 전원이 문의방 멤버이므로, 아직 없는 문의방엔 조용히(시스템 메시지 없이) 등록한다. */
    public List<ChatRoomSummaryResponse> listRooms(UUID adminId, boolean isAdmin) {
        accessPolicy.checkAdmin(isAdmin);
        // ponytail: 같은 관리자의 동시 첫 조회는 UNIQUE(room_id,user_id) 위반으로 한쪽만 실패(재조회로 회복). 전체 방을 한 번에 싣는다 — 방이 수천 개가 되면 페이지네이션.
        chatMemberRepository.saveAll(chatRoomRepository.findRoomIdsWithoutMember(ChatRoomType.INQUIRY, adminId).stream()
                .map(roomId -> ChatMember.join(roomId, adminId, null))
                .toList());
        return assembler.toSummaries(chatRoomRepository.findAllByDeletedAtIsNull(), adminId, true);
    }

    @Transactional(readOnly = true)
    public List<ChatSourceMemberResponse> listSourceMembers(ChatSourceType type, UUID sourceId, boolean isAdmin) {
        accessPolicy.checkAdmin(isAdmin);
        List<UUID> userIds = type == ChatSourceType.GATHERING
                ? adminApplicationService.listConfirmedMemberUserIds(sourceId)
                : matchingService.listGroupMemberUserIds(sourceId);
        Map<UUID, User> users = userService.findUsersByIds(userIds);
        return userIds.stream()
                .map(users::get)
                .filter(user -> user != null && !user.isWithdrawn())
                .map(ChatSourceMemberResponse::from)
                .toList();
    }

    public void addMembers(UUID roomId, boolean isAdmin, ChatMemberAddRequest request) {
        ChatRoom room = findRoom(roomId);
        accessPolicy.checkManageGroup(room, isAdmin);
        joinMembers(room, new LinkedHashSet<>(request.getUserIds()));
    }

    public void kickMember(UUID roomId, UUID userId, boolean isAdmin) {
        ChatRoom room = findRoom(roomId);
        accessPolicy.checkManageGroup(room, isAdmin);
        ChatMember member = chatMemberRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        accessPolicy.checkMember(member);
        member.leave();
        User user = userService.findUsersByIds(List.of(userId)).get(userId);
        createSystemMessage(room, ChatSystemKind.KICKED, Arrays.asList(assembler.displayNickname(user, room, true)));
        eventPublisher.publishEvent(new ChatMemberChangedEvent(roomId, List.of(userId)));
    }

    public void changeNotice(UUID roomId, UUID adminId, boolean isAdmin, ChatNoticeUpdateRequest request) {
        accessPolicy.checkAdmin(isAdmin);
        ChatRoom room = findRoom(roomId);
        UUID messageId = request.getMessageId();
        if (messageId != null) {
            chatMessageRepository.findByIdAndRoomId(messageId, roomId)
                    .filter(m -> !m.isDeleted() && m.getType() != ChatMessageType.SYSTEM)
                    .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        }
        room.changeNotice(messageId);
        if (messageId != null) {
            // 문의방에선 사용자에게 관리자 닉네임이 드러나지 않게 "와썹하우스"로 남긴다.
            User admin = userService.findUsersByIds(List.of(adminId)).get(adminId);
            createSystemMessage(room, ChatSystemKind.NOTICE_SET,
                    Arrays.asList(assembler.displayNickname(admin, room, false)));
        }
        eventPublisher.publishEvent(new ChatNoticeChangedEvent(roomId, messageId));
    }

    public void deleteRoom(UUID roomId, boolean isAdmin) {
        ChatRoom room = findRoom(roomId);
        accessPolicy.checkManageGroup(room, isAdmin);
        room.delete();
    }

    public void muteUser(UUID userId, UUID adminId, boolean isAdmin, ChatMuteRequest request) {
        accessPolicy.checkAdmin(isAdmin);
        User target = userService.findUsersByIds(List.of(userId)).get(userId);
        if (target == null || target.isWithdrawn()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        chatMuteRepository.findById(userId).ifPresentOrElse(
                mute -> mute.update(adminId, request.getReason()),
                () -> chatMuteRepository.save(ChatMute.of(userId, adminId, request.getReason())));
    }

    public void unmuteUser(UUID userId, boolean isAdmin) {
        accessPolicy.checkAdmin(isAdmin);
        chatMuteRepository.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public List<ChatReportResponse> listReports(ChatReportStatus status, boolean isAdmin) {
        accessPolicy.checkAdmin(isAdmin);
        List<ChatReport> reports = status == null
                ? chatReportRepository.findAllByOrderByCreatedAtDesc()
                : chatReportRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return assembler.toReports(reports);
    }

    public void changeReportStatus(UUID reportId, boolean isAdmin, ChatReportStatusRequest request) {
        accessPolicy.checkAdmin(isAdmin);
        ChatReport report = chatReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_REPORT_NOT_FOUND));
        report.changeStatus(request.getStatus());
    }

    /** 초대·재초대. 이미 참여 중이면 건너뛰고, 실제로 들어온 사람만 JOINED 시스템 메시지 1건에 담는다. */
    private void joinMembers(ChatRoom room, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Map<UUID, User> users = userService.findUsersByIds(userIds);
        for (UUID userId : userIds) {
            User user = users.get(userId);
            if (user == null || user.isWithdrawn()) {
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }
        }
        Map<UUID, ChatMember> existing = chatMemberRepository.findAllByRoomIdAndUserIdIn(room.getId(), userIds).stream()
                .collect(Collectors.toMap(ChatMember::getUserId, Function.identity()));
        // 합류 시점까지의 기록은 읽은 것으로 둔다(과거 기록은 보이되 배지·안 읽은 사람 수엔 안 잡힘)
        UUID latestMessageId = chatMessageRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId())
                .map(ChatMessage::getId).orElse(null);

        List<UUID> joinedIds = new ArrayList<>();
        List<String> joinedNicknames = new ArrayList<>();
        for (UUID userId : userIds) {
            ChatMember member = existing.get(userId);
            if (member == null) {
                chatMemberRepository.save(ChatMember.join(room.getId(), userId, latestMessageId));
            } else if (!member.isActive()) {
                member.rejoin(latestMessageId);
            } else {
                continue;
            }
            joinedIds.add(userId);
            joinedNicknames.add(users.get(userId).getNickname());
        }
        if (joinedIds.isEmpty()) {
            return;
        }
        createSystemMessage(room, ChatSystemKind.JOINED, joinedNicknames);
        eventPublisher.publishEvent(new ChatMemberChangedEvent(room.getId(), joinedIds));
    }

    private void createSystemMessage(ChatRoom room, ChatSystemKind kind, List<String> nicknames) {
        ChatMessage message = chatMessageRepository.save(ChatMessage.system(room.getId(), kind, Map.of(NICKNAMES, nicknames)));
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(room.getId(), message.getId()));
    }

    private ChatRoom findRoom(UUID roomId) {
        return chatRoomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
