package com.whatsuphouse.backend.domain.chat.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatGroupRoomCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMemberAddRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageSendRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatNoticeUpdateRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatMessageResponse;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSystemKind;
import com.whatsuphouse.backend.domain.chat.repository.ChatMemberRepository;
import com.whatsuphouse.backend.domain.chat.repository.ChatRoomRepository;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import com.whatsuphouse.backend.global.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

/** 실제 JPA 쿼리(안읽은 수·커서·멤버십)를 H2로 검증한다. 타 도메인 서비스·스토리지만 목. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestJpaConfig.class, ChatService.class, AdminChatService.class, ChatAccessPolicy.class,
        ChatCipher.class, ChatResponseAssembler.class})
@ActiveProfiles("test")
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private AdminChatService adminChatService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private AdminApplicationService adminApplicationService;

    @MockitoBean
    private MatchingService matchingService;

    private final Map<UUID, User> users = new HashMap<>();
    private UUID userId;
    private UUID adminId;
    private UUID friendId;

    @BeforeEach
    void setUp() {
        userId = addUser("문의자", false);
        adminId = addUser("운영자김", true);
        friendId = addUser("친구", false);

        given(userService.findUsersByIds(anyCollection())).willAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return users.entrySet().stream().filter(e -> ids.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
        given(userService.listActiveAdminIds()).willReturn(List.of(adminId));
        given(storageService.createSignedUrls(any(), anyCollection(), anyInt())).willReturn(Map.of());
    }

    @Test
    @DisplayName("문의방은 사용자당 1개: 다시 열면 같은 방(숨김 해제), DB도 UNIQUE로 막는다")
    void openInquiryRoom_singlePerUser() {
        // given
        UUID roomId = chatService.openInquiryRoom(userId, false).getRoomId();
        chatService.hideRoom(roomId, userId, false);
        assertThat(chatService.listRooms(userId, false)).isEmpty();

        // when
        UUID reopenedId = chatService.openInquiryRoom(userId, false).getRoomId();

        // then
        assertThat(reopenedId).isEqualTo(roomId);
        assertThat(chatService.listRooms(userId, false)).extracting(ChatRoomSummaryResponse::getId).containsExactly(roomId);
        assertThat(chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(roomId))
                .extracting(ChatMember::getUserId)
                .containsExactlyInAnyOrder(userId, adminId); // 문의 사용자 + 관리자 전원
        assertThatThrownBy(() -> chatRoomRepository.saveAndFlush(ChatRoom.inquiry(userId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("문의방에서 관리자 메시지는 사용자에게 와썹하우스로, 관리자에겐 실제 닉네임으로 보인다")
    void inquiryAdminSender_displayedAsWhatsupHouseToUser() {
        // given
        UUID roomId = chatService.openInquiryRoom(userId, false).getRoomId();
        chatService.sendMessage(roomId, adminId, true, text("안녕하세요, 무엇을 도와드릴까요?"));

        // when
        ChatMessageResponse userView = chatService.listMessages(roomId, userId, false, null, null, 50).get(0);
        ChatMessageResponse adminView = chatService.listMessages(roomId, adminId, true, null, null, 50).get(0);

        // then
        assertThat(userView.getContent()).isEqualTo("안녕하세요, 무엇을 도와드릴까요?");
        assertThat(userView.getSender().getNickname()).isEqualTo(ChatResponseAssembler.INQUIRY_DISPLAY_NAME);
        assertThat(adminView.getSender().getNickname()).isEqualTo("운영자김");
    }

    @Test
    @DisplayName("나갔다 재초대되면 left_at이 NULL로 돌아오고, 나가 있던 동안을 포함한 방 전체 기록이 보인다")
    void reinvite_restoresMembershipAndFullHistory() {
        // given
        UUID roomId = createGroup(friendId);
        chatService.sendMessage(roomId, friendId, false, text("나가기 전"));
        chatService.leaveRoom(roomId, friendId);
        chatService.sendMessage(roomId, adminId, true, text("나가 있던 동안"));
        assertThatThrownBy(() -> chatService.listMessages(roomId, friendId, false, null, null, 50))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_NOT_MEMBER);

        // when
        adminChatService.addMembers(roomId, true, addRequest(friendId));

        // then
        assertThat(chatMemberRepository.findByRoomIdAndUserId(roomId, friendId).orElseThrow().getLeftAt()).isNull();
        List<ChatMessageResponse> history = chatService.listMessages(roomId, friendId, false, null, null, 50);
        assertThat(history).extracting(ChatMessageResponse::getContent).contains("나가기 전", "나가 있던 동안");
        assertThat(history).filteredOn(m -> m.getType() == ChatMessageType.SYSTEM)
                .extracting(ChatMessageResponse::getSystemKind)
                .containsOnly(ChatSystemKind.JOINED); // 생성 시 초대 + 재초대. 조용히 나가기는 기록 없음
        assertThat(history).filteredOn(m -> m.getType() == ChatMessageType.SYSTEM).hasSize(2);

        // 커서: 마지막 메시지 이전을 조회해도 같은 전체 기록(마지막 제외)이 나온다
        UUID lastId = history.get(history.size() - 1).getId();
        assertThat(chatService.listMessages(roomId, friendId, false, lastId, null, 50)).hasSize(history.size() - 1);
    }

    @Test
    @DisplayName("안읽은 수는 비SYSTEM 메시지만 센다(JOINED·NOTICE_SET 제외, SYSTEM의 안 읽은 사람 수는 0)")
    void unreadCount_excludesSystemMessages() {
        // given: 생성 시 JOINED 시스템 메시지 1건
        UUID roomId = createGroup(friendId);
        ChatMessageResponse first = chatService.sendMessage(roomId, adminId, true, text("첫 공지 후보"));
        chatService.sendMessage(roomId, adminId, true, text("두 번째"));
        ChatNoticeUpdateRequest notice = new ChatNoticeUpdateRequest();
        ReflectionTestUtils.setField(notice, "messageId", first.getId());
        adminChatService.changeNotice(roomId, adminId, true, notice); // NOTICE_SET 시스템 메시지

        // when
        ChatRoomSummaryResponse summary = chatService.listRooms(friendId, false).get(0);

        // then
        assertThat(summary.getUnreadCount()).isEqualTo(2);
        List<ChatMessageResponse> messages = chatService.listMessages(roomId, friendId, false, null, null, 50);
        assertThat(messages).filteredOn(m -> m.getType() == ChatMessageType.SYSTEM)
                .hasSize(2)
                .allSatisfy(m -> assertThat(m.getUnreadCount()).isZero());
        assertThat(messages).filteredOn(m -> m.getType() == ChatMessageType.TEXT)
                .allSatisfy(m -> assertThat(m.getUnreadCount()).isEqualTo(1)); // 친구가 아직 안 읽음

        // 읽음 처리하면 0
        chatService.markRead(roomId, friendId, messages.get(messages.size() - 1).getId());
        assertThat(chatService.listRooms(friendId, false).get(0).getUnreadCount()).isZero();
    }

    private UUID createGroup(UUID... memberIds) {
        ChatGroupRoomCreateRequest request = new ChatGroupRoomCreateRequest();
        ReflectionTestUtils.setField(request, "name", "10월 게더링");
        ReflectionTestUtils.setField(request, "memberIds", List.of(memberIds));
        return adminChatService.createGroupRoom(adminId, true, request).getRoomId();
    }

    private ChatMemberAddRequest addRequest(UUID... userIds) {
        ChatMemberAddRequest request = new ChatMemberAddRequest();
        ReflectionTestUtils.setField(request, "userIds", List.of(userIds));
        return request;
    }

    private ChatMessageSendRequest text(String content) {
        ChatMessageSendRequest request = new ChatMessageSendRequest();
        ReflectionTestUtils.setField(request, "type", ChatMessageType.TEXT);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
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
