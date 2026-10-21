package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.event.ApplicationStatusChangedEvent;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatGroupRoomCreateRequest;
import com.whatsuphouse.backend.domain.chat.dto.request.ChatMessageSendRequest;
import com.whatsuphouse.backend.domain.chat.dto.response.ChatRoomIdResponse;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import com.whatsuphouse.backend.domain.chat.enums.ChatSourceType;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableConfirmResponse;
import com.whatsuphouse.backend.domain.matching.entity.Attendance;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.NoticeChannel;
import com.whatsuphouse.backend.domain.matching.enums.NoticeKind;
import com.whatsuphouse.backend.domain.matching.repository.AttendanceRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.notification.event.DiningTableConfirmedEvent;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 우연한 식탁 자동 확정 파이프라인. (설계 4.6, KAN-346)
 * 1~2. 하드 조건 최종 검증 → 테이블 CONFIRMED, 멤버 신청 CONFIRMED(배정 회차 = 테이블 회차), 참석 SCHEDULED. 여기까지 한 트랜잭션.
 *      위반이면 보류(상태·confirm_at 유지) + 예외함 CONFLICT(그 테이블에 열린 CONFLICT가 없을 때만).
 * 3. 식당 배정, 4. 채팅방 + 운영자 메시지 2개, 5. 확정 알림. 단계마다 새 트랜잭션이라 실패해도 확정은 되돌아가지 않고 예외함에 남는다.
 * 4단계가 실패한 테이블은 스케줄러가 5분마다 최대 3회 다시 시도한다(retryMissingChatRooms, KAN-438).
 * 모든 단계가 REQUIRES_NEW라 호출 측 트랜잭션과 섞이지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiningConfirmService {

    // 같은 NOTIFICATION 유형 안에서 재시도 API가 자기 실패 건만 닫도록 사유 앞에 붙인다.
    static final String CHAT_FAILURE_PREFIX = "[채팅방] ";
    static final String NOTIFY_FAILURE_PREFIX = "[알림] ";
    private static final String RETRY_NOTE = "재시도 성공";
    private static final String AUTO_RETRY_NOTE = "자동 재시도 성공";
    static final int MAX_CHAT_RETRIES = 3;
    private static final Duration CHAT_RETRY_DELAY = Duration.ofMinutes(5);
    private static final int ROOM_NAME_MAX_LENGTH = 100;
    private static final DateTimeFormatter NOTICE_DATE = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN);
    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MEETING_DATE = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);

    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final AttendanceRepository attendanceRepository;
    private final GatheringService gatheringService;
    private final DiningMatchService diningMatchService;
    private final AdminApplicationService adminApplicationService;
    private final SessionVenueService sessionVenueService;
    private final ExceptionCaseService exceptionCaseService;
    private final AdminChatService adminChatService;
    private final ChatService chatService;
    private final DiningTableExplanationService diningTableExplanationService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final NoticeLogService noticeLogService;

    // 자동 확정 채팅방 개설자(관리자 회원 ID). 비어 있으면 활성 관리자 중 한 명.
    @Value("${dining.system-admin-id:}")
    private String systemAdminIdSetting;

    /** 확정 시각이 지난 제안 테이블을 테이블마다 따로 확정한다. 한 테이블의 실패가 다른 테이블을 막지 않는다. */
    public void confirmDueTables() {
        List<UUID> tableIds = diningTableRepository.findIdsByStatusAndConfirmAtBefore(
                DiningTableStatus.PROPOSED, LocalDateTime.now());
        for (UUID tableId : tableIds) {
            try {
                confirmTable(tableId);
            } catch (Exception e) {
                log.error("테이블 자동 확정 실패: tableId={}", tableId, e);
            }
        }
    }

    /** 관리자 즉시 확정: confirm_at을 지금으로 당기고 스케줄러를 기다리지 않고 파이프라인을 한 번 돌린다. */
    public DiningTableConfirmResponse confirmNow(UUID tableId) {
        runInNewTransaction(() -> {
            DiningTable table = findTable(tableId);
            if (table.getStatus() != DiningTableStatus.PROPOSED) {
                throw new CustomException(ErrorCode.TABLE_NOT_PROPOSED);
            }
            table.changeConfirmAt(LocalDateTime.now());
        });
        confirmTable(tableId);
        return inNewTransaction(() -> DiningTableConfirmResponse.from(findTable(tableId)));
    }

    /** 채팅방 재시도: 확정 테이블에 채팅방이 없으면 4단계를 다시 한다(요청한 관리자가 개설자). 성공하면 채팅방 실패 예외를 닫는다. */
    public ChatRoomIdResponse retryChatRoom(UUID tableId, UUID adminId) {
        UUID roomId;
        try {
            roomId = inNewTransaction(() -> {
                // 자동 재시도와 겹쳐 방이 두 개 생기지 않게 행을 먼저 잠근다. 잠근 뒤 읽어야 chat_room_id가 최신이다.
                diningTableRepository.findByIdForUpdate(tableId);
                requireConfirmed(tableId);
                return openChatRoom(tableId, adminId);
            });
        } catch (RuntimeException e) {
            noticeLogService.recordFailure(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT, e);
            throw e;
        }
        noticeLogService.recordSuccess(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT);
        exceptionCaseService.resolveOpen(ExceptionCaseType.NOTIFICATION, tableId, CHAT_FAILURE_PREFIX, adminId, RETRY_NOTE);
        return new ChatRoomIdResponse(roomId);
    }

    /**
     * 채팅방 자동 재시도(5분 주기 스케줄러): 확정된 지 5분이 지났는데 채팅방이 없는 테이블마다 최대 3회 다시 만든다(개설자 = 시스템 관리자).
     * 성공하면 채팅방 실패 예외를 닫고, 3회 모두 실패하면 채팅방 실패 예외(NOTIFICATION)를 열린 채로 둔다. 행사일이 지난 테이블은 건너뛴다. (KAN-438)
     */
    public void retryMissingChatRooms() {
        LocalDateTime now = LocalDateTime.now();
        for (DiningTable table : diningTableRepository.findChatRoomRetryTargets(DiningTableStatus.CONFIRMED,
                MAX_CHAT_RETRIES, now.minus(CHAT_RETRY_DELAY), now.toLocalDate())) {
            if (table.getSession().getStatus() == GatheringSessionStatus.CANCELLED) {
                continue;
            }
            try {
                retryChatRoomAutomatically(table.getId(), table.getChatRetryCount() + 1);
            } catch (Exception e) {
                log.error("테이블 채팅방 자동 재시도 처리 실패: tableId={}", table.getId(), e);
            }
        }
    }

    // 횟수 선점(커밋)과 시도가 다른 트랜잭션이라, 시도가 실패해도 횟수는 남는다. 실제로 방을 만든 시도마다 발송 이력(CONFIRM_MESSAGE/CHAT)을 남긴다.
    private void retryChatRoomAutomatically(UUID tableId, int attempt) {
        if (inNewTransaction(() -> diningTableRepository.increaseChatRetryCount(tableId, MAX_CHAT_RETRIES)) == 0) {
            return; // 이미 방이 생겼거나 다른 실행이 횟수를 채웠다
        }
        UUID roomId;
        try {
            roomId = inNewTransaction(() -> {
                // 테이블 행을 잠가 다른 인스턴스의 재시도·수동 재시도와 직렬화한다. 그사이 방이 생겼으면 그쪽이 이력을 남겼으므로 건너뛴다.
                DiningTable locked = diningTableRepository.findByIdForUpdate(tableId).orElse(null);
                if (locked == null || locked.getStatus() != DiningTableStatus.CONFIRMED || locked.getChatRoomId() != null) {
                    return null;
                }
                return openChatRoom(tableId, systemAdminId());
            });
        } catch (Exception e) {
            log.warn("테이블 채팅방 자동 재시도 실패 {}/{}: tableId={}", attempt, MAX_CHAT_RETRIES, tableId, e);
            noticeLogService.recordFailure(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT, e);
            if (attempt >= MAX_CHAT_RETRIES
                    && exceptionCaseService.listOpen(ExceptionCaseType.NOTIFICATION, tableId, CHAT_FAILURE_PREFIX).isEmpty()) {
                openCase(ExceptionCaseType.NOTIFICATION, tableId,
                        CHAT_FAILURE_PREFIX + "채팅방 자동 재시도 " + MAX_CHAT_RETRIES + "회 실패: " + e.getMessage());
            }
            return;
        }
        if (roomId != null) {
            noticeLogService.recordSuccess(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT);
            exceptionCaseService.resolveOpen(ExceptionCaseType.NOTIFICATION, tableId, CHAT_FAILURE_PREFIX, null, AUTO_RETRY_NOTE);
        }
    }

    /** 확정 알림 재발송(멤버 전원). 성공하면 알림 실패 예외를 닫는다. */
    public void retryNotifications(UUID tableId, UUID adminId) {
        try {
            runInNewTransaction(() -> {
                requireConfirmed(tableId);
                publishConfirmed(tableId);
            });
        } catch (RuntimeException e) {
            noticeLogService.recordFailure(tableId, NoticeKind.NOTIFICATION, NoticeChannel.IN_APP, e);
            throw e;
        }
        noticeLogService.recordSuccess(tableId, NoticeKind.NOTIFICATION, NoticeChannel.IN_APP);
        exceptionCaseService.resolveOpen(ExceptionCaseType.NOTIFICATION, tableId, NOTIFY_FAILURE_PREFIX, adminId, RETRY_NOTE);
    }

    // 파이프라인 1회. 1~2단계에서 확정되지 않았으면(이미 처리됨·보류) 이후 단계는 건너뛴다.
    void confirmTable(UUID tableId) {
        if (!inNewTransaction(() -> confirm(tableId))) {
            return;
        }
        assignVenue(tableId);
        createChatRoom(tableId);
        notifyMembers(tableId);
    }

    // ── 1~2. 검증·확정 ────────────────────────────────────────────────────────

    // 회차 행을 먼저 잠가 같은 회차의 매칭 재실행(제안 테이블 해체)·다른 확정과 직렬화한 뒤 테이블을 새로 읽는다.
    // 테이블 행도 잠가 수동 조정·취소 재조정(KAN-347, 테이블 행 잠금)이 끝난 뒤의 멤버 구성으로 검증한다.
    private boolean confirm(UUID tableId) {
        UUID sessionId = diningTableRepository.findSessionIdById(tableId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        GatheringSession session = gatheringService.lockRandomTableSession(sessionId);
        DiningTable table = diningTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
        if (table.getStatus() != DiningTableStatus.PROPOSED || session.getStatus() == GatheringSessionStatus.CANCELLED) {
            return false;
        }
        List<DiningTableMember> members = findActiveMembers(tableId);
        if (!diningMatchService.satisfiesHardConditions(table,
                members.stream().map(DiningTableMember::getApplication).toList())) {
            // 보류: 매 주기 다시 검증한다. 운영자가 멤버를 고치면 다음 주기에 확정된다.
            if (exceptionCaseService.listOpen(ExceptionCaseType.CONFLICT, tableId, "").isEmpty()) {
                exceptionCaseService.open(ExceptionCaseType.CONFLICT, sessionId, tableId, null,
                        "하드 조건(인원·나이 차·제외 관계·희망 지역) 위반으로 자동 확정을 보류했습니다. 멤버를 조정해 주세요.");
            }
            return false;
        }

        table.confirm();
        Set<UUID> scheduled = attendanceRepository.findByTableMemberIdIn(
                        members.stream().map(DiningTableMember::getId).toList()).stream()
                .map(Attendance::getTableMemberId)
                .collect(Collectors.toSet());
        for (DiningTableMember member : members) {
            member.getApplication().confirmMatch(session);
            eventPublisher.publishEvent(new ApplicationStatusChangedEvent(member.getApplication(), "테이블 확정"));
            // v1 멤버 이동으로 이미 참석 행이 있는 멤버는 건너뛴다(table_member_id UNIQUE).
            if (!scheduled.contains(member.getId())) {
                attendanceRepository.save(Attendance.schedule(member.getId()));
            }
        }
        return true;
    }

    // ── 3. 식당 ─────────────────────────────────────────────────────────────

    void assignVenue(UUID tableId) {
        try {
            runInNewTransaction(() -> {
                DiningTable table = findTable(tableId);
                if (table.getVenueId() != null) {
                    return; // 유예 중 운영자가 미리 배정
                }
                UUID sessionId = table.getSession().getId();
                // 테이블 지역 식당 중에서 고른다(지역 매칭 전 테이블은 region null → 풀 전체). (KAN-431)
                sessionVenueService.useFirstAvailableVenue(sessionId, table.getDisplayRegion(), preferredPriceRanges(tableId))
                        .ifPresentOrElse(table::assignVenue,
                                () -> exceptionCaseService.open(ExceptionCaseType.VENUE, sessionId, tableId, null,
                                        "이번 회차 식당에 남은 자리가 없어 식당을 배정하지 못했습니다."));
            });
        } catch (Exception e) {
            log.warn("테이블 식당 배정 실패: tableId={}", tableId, e);
            openCase(ExceptionCaseType.VENUE, tableId, "식당 배정 실패: " + e.getMessage());
        }
    }

    // 테이블 멤버가 PRICE_RANGE에 답한 선택지의 교집합. 상관 없음을 고른 멤버는 뺀다. 답한 멤버가 없거나 겹치지 않으면 빈 집합(선호 없음). (KAN-407)
    Set<String> preferredPriceRanges(UUID tableId) {
        List<UUID> applicationIds = findActiveMembers(tableId).stream().map(member -> member.getApplication().getId()).toList();
        Map<UUID, Set<String>> byApplication = new HashMap<>();
        for (ApplicationAnswer answer : adminApplicationService.findAnswers(applicationIds)) {
            if (answer.getQuestion().getDeletedAt() == null
                    && answer.getQuestion().getReservedKey() == ReservedQuestionKey.PRICE_RANGE
                    && answer.getValue() != null) {
                Set<String> choices = DiningMatchService.values(answer.getValue().get("value"));
                // 상관 없음(HIGH 선택지)을 고른 멤버는 테이블 가격을 제약하지 않는다.
                if (!choices.contains(PriceRangeOption.HIGH.getLabel())) {
                    byApplication.put(answer.getApplication().getId(), choices);
                }
            }
        }
        Set<String> common = null;
        for (Set<String> choices : byApplication.values()) {
            if (common == null) {
                common = new HashSet<>(choices);
            } else {
                common.retainAll(choices);
            }
        }
        return common != null ? common : Set.of();
    }

    // ── 4. 채팅방 ───────────────────────────────────────────────────────────

    private void createChatRoom(UUID tableId) {
        try {
            inNewTransaction(() -> openChatRoom(tableId, systemAdminId()));
            noticeLogService.recordSuccess(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT);
        } catch (Exception e) {
            log.warn("테이블 채팅방 생성 실패: tableId={}", tableId, e);
            noticeLogService.recordFailure(tableId, NoticeKind.CONFIRM_MESSAGE, NoticeChannel.CHAT, e);
            openCase(ExceptionCaseType.NOTIFICATION, tableId, CHAT_FAILURE_PREFIX + "채팅방 생성 실패: " + e.getMessage());
        }
    }

    // 채팅방이 없으면 만들고 개설자(운영자) 이름으로 메시지 2개(장점 문구, 일시·지역·식당)를 남긴다. (설계 6.4)
    // 취소 정책은 채팅방에 올리지 않는다(테이블 상세에서 안내, KAN-442).
    // 방·chat_room_id·메시지가 한 트랜잭션이라 중간에 실패하면 모두 되돌아간다. 방이 이미 있으면 다시 보내지 않는다.
    private UUID openChatRoom(UUID tableId, UUID creatorId) {
        DiningTable table = findTable(tableId);
        if (table.getChatRoomId() != null) {
            return table.getChatRoomId();
        }
        UUID roomId = adminChatService.createGroupRoom(creatorId, true, new ChatGroupRoomCreateRequest(
                roomName(table), findMemberUserIds(tableId), ChatSourceType.DINING_TABLE, tableId)).getRoomId();
        table.linkChatRoom(roomId);
        List<Application> applications = findActiveMembers(tableId).stream().map(DiningTableMember::getApplication).toList();
        sendAsOperator(roomId, creatorId, diningTableExplanationService.explain(table, applications).strengths().get(0));
        sendAsOperator(roomId, creatorId, meetingMessage(table));
        return roomId;
    }

    // 운영자 이름 메시지(TEXT)라 SYSTEM 공지와 달리 채팅 푸시도 간다. 리마인드 안내도 쓴다. (KAN-438)
    void sendAsOperator(UUID roomId, UUID senderId, String text) {
        chatService.sendMessage(roomId, senderId, true, new ChatMessageSendRequest(ChatMessageType.TEXT, text));
    }

    // 예: "10월 22일 목요일 오후 7시 강남/신논현 브리비트에서 만나요! https://naver.me/…"
    // 식당이 없으면 정해지면 알려 준다는 안내, 지도 링크가 없으면 링크를 뺀다.
    private String meetingMessage(DiningTable table) {
        Venue venue = table.getVenueId() == null ? null : sessionVenueService.findVenue(table.getVenueId()).orElse(null);
        List<String> parts = new ArrayList<>();
        parts.add(meetingTime(table.getSession().getEventDate(), table.getSession().getStartTime()));
        if (table.getDisplayRegion() != null) {
            parts.add(table.getDisplayRegion());
        }
        if (venue != null) {
            parts.add(venue.getName());
        }
        String message = String.join(" ", parts) + "에서 만나요!";
        if (venue == null) {
            return message + " 식당은 정해지면 이 방에서 알려드릴게요.";
        }
        return venue.getMapUrl() == null || venue.getMapUrl().isBlank() ? message : message + " " + venue.getMapUrl();
    }

    // "10월 22일 목요일 오후 7시", 분이 0이 아니면 " 30분"을 붙인다. 시작 시간이 없으면 날짜만.
    static String meetingTime(LocalDate date, LocalTime time) {
        String day = date.format(MEETING_DATE);
        if (time == null) {
            return day;
        }
        int hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        return day + (time.getHour() < 12 ? " 오전 " : " 오후 ") + hour + "시"
                + (time.getMinute() != 0 ? " " + time.getMinute() + "분" : "");
    }

    // 설정값이 활성 관리자가 아니면(없는 id·탈퇴·정지) 활성 관리자 첫 명으로 대신한다.
    // 운영자 이름 메시지는 활성 계정만 보낼 수 있어서, 설정 실수 하나로 모든 채팅방 생성이 실패하지 않게 한다.
    UUID systemAdminId() {
        List<UUID> activeAdminIds = userService.listActiveAdminIds();
        if (systemAdminIdSetting != null && !systemAdminIdSetting.isBlank()) {
            UUID configured = UUID.fromString(systemAdminIdSetting.trim());
            if (activeAdminIds.contains(configured)) {
                return configured;
            }
            log.warn("dining.system-admin-id 가 활성 관리자가 아니라 다른 활성 관리자로 대신합니다: {}", configured);
        }
        return activeAdminIds.stream().findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.SYSTEM_ADMIN_NOT_FOUND));
    }

    // 예: "우연한 식탁 10/3 강남"
    private static String roomName(DiningTable table) {
        String region = table.getDisplayRegion();
        String name = "우연한 식탁 " + table.getEventDate().getMonthValue() + "/" + table.getEventDate().getDayOfMonth()
                + (region != null ? " " + region : "");
        return name.length() > ROOM_NAME_MAX_LENGTH ? name.substring(0, ROOM_NAME_MAX_LENGTH) : name;
    }

    // 리마인드(KAN-349) 채팅 안내: 첫 줄 + 시간·지역·식당. 취소 정책은 넣지 않는다(KAN-442).
    String tableNotice(DiningTable table, String headline) {
        GatheringSession session = table.getSession();
        String time = session.getStartTime() == null ? ""
                : " " + session.getStartTime().format(NOTICE_TIME)
                + (session.getEndTime() != null ? "~" + session.getEndTime().format(NOTICE_TIME) : "");
        Venue venue = table.getVenueId() == null ? null : sessionVenueService.findVenue(table.getVenueId()).orElse(null);
        List<String> lines = new ArrayList<>();
        lines.add(headline);
        lines.add("일시: " + session.getEventDate().format(NOTICE_DATE) + time);
        if (table.getDisplayRegion() != null) {
            lines.add("지역: " + table.getDisplayRegion());
        }
        lines.add("식당: " + (venue != null ? venue.getName() + " (" + venue.getAddress() + ")"
                : "배정 중이에요. 정해지면 이 방에서 알려드릴게요."));
        return String.join("\n", lines);
    }

    // ── 5. 알림 ─────────────────────────────────────────────────────────────

    private void notifyMembers(UUID tableId) {
        try {
            runInNewTransaction(() -> publishConfirmed(tableId));
            noticeLogService.recordSuccess(tableId, NoticeKind.NOTIFICATION, NoticeChannel.IN_APP);
        } catch (Exception e) {
            log.warn("테이블 확정 알림 실패: tableId={}", tableId, e);
            noticeLogService.recordFailure(tableId, NoticeKind.NOTIFICATION, NoticeChannel.IN_APP, e);
            openCase(ExceptionCaseType.NOTIFICATION, tableId, NOTIFY_FAILURE_PREFIX + "확정 알림 발송 실패: " + e.getMessage());
        }
    }

    // 리스너(DiningConfirmNotificationListener)가 같은 트랜잭션에서 알림을 적재한다.
    private void publishConfirmed(UUID tableId) {
        DiningTable table = findTable(tableId);
        eventPublisher.publishEvent(new DiningTableConfirmedEvent(tableId, table.getSession().getId(),
                findMemberUserIds(tableId)));
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    private DiningTable findTable(UUID tableId) {
        return diningTableRepository.findById(tableId)
                .filter(table -> table.getDeletedAt() == null)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
    }

    private void requireConfirmed(UUID tableId) {
        if (findTable(tableId).getStatus() != DiningTableStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.TABLE_NOT_CONFIRMED);
        }
    }

    // 취소(soft delete)된 신청의 멤버 행은 뺀다.
    private List<DiningTableMember> findActiveMembers(UUID tableId) {
        return diningTableMemberRepository.findByTableIdWithApplication(tableId).stream()
                .filter(member -> member.getApplication().getDeletedAt() == null)
                .toList();
    }

    // 채팅방 초대·알림 대상: 탈퇴하지 않은 회원 멤버
    private List<UUID> findMemberUserIds(UUID tableId) {
        return findActiveMembers(tableId).stream()
                .map(member -> member.getApplication().getUser())
                .filter(Objects::nonNull)
                .filter(user -> !user.isWithdrawn())
                .map(User::getId)
                .distinct()
                .toList();
    }

    // 실패한 단계의 트랜잭션은 이미 롤백됐으므로 예외함 기록은 새 트랜잭션에서 남긴다.
    private void openCase(ExceptionCaseType type, UUID tableId, String reason) {
        runInNewTransaction(() -> exceptionCaseService.open(type,
                diningTableRepository.findSessionIdById(tableId).orElse(null), tableId, null, reason));
    }

    private <T> T inNewTransaction(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }

    private void runInNewTransaction(Runnable work) {
        inNewTransaction(() -> {
            work.run();
            return null;
        });
    }
}
