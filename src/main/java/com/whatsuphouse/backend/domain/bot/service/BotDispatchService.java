package com.whatsuphouse.backend.domain.bot.service;

import com.whatsuphouse.backend.domain.bot.dto.response.BotPendingResponse;
import com.whatsuphouse.backend.domain.bot.dto.response.BotSettingResponse;
import com.whatsuphouse.backend.domain.bot.entity.BotDispatch;
import com.whatsuphouse.backend.domain.bot.entity.BotSetting;
import com.whatsuphouse.backend.domain.bot.enums.BotDispatchStatus;
import com.whatsuphouse.backend.domain.bot.repository.BotDispatchRepository;
import com.whatsuphouse.backend.domain.bot.repository.BotSettingRepository;
import com.whatsuphouse.backend.domain.chat.service.ChatService;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 1:1 문의방 그록봇 자동응답 대기열(KAN-406).
 * 대상: bot.enabled && 전역 토글 ON && (허용 목록 또는 데모 회원) && 방 botPaused=false 인 고객 본인 메시지.
 * 흐름: 메시지 → PENDING(같은 방 대기 건은 디바운스로 마지막 메시지 하나로 묶음) → Slack 신호(BotSignalService)
 *      → 에이전트가 GET /api/bot/pending으로 lease(LEASED, 5분) → 답변 API. 만료되면 PENDING으로, 3회 만료면 FAILED.
 * 미처리(5분)·lease 소진·한도 초과는 담당자 이관(handOff). 봇은 데이터를 바꾸지 않는다(답변 게시·이관 표시만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotDispatchService {

    public static final String HANDOFF_NOTICE = "담당자에게 연결했어요. 곧 답변드릴게요.";
    static final String TIMEOUT_REASON = "응답 시간 초과";
    static final String LIMIT_REASON = "발송 한도 초과";
    static final String PAUSED_REASON = "담당자 응대 중";
    static final String NOT_TARGET_REASON = "자동응답 대상 아님";
    static final String SUPERSEDED_REASON = "대체됨";
    static final String PAYLOAD_FAILED_REASON = "문의 내용 조회 실패";
    static final int MAX_PENDING_LIMIT = 20;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration UNCLAIMED_TIMEOUT = Duration.ofMinutes(5);

    private final BotDispatchRepository botDispatchRepository;
    private final BotSettingRepository botSettingRepository;
    private final ChatService chatService;
    private final UserService userService;
    private final ExceptionCaseService exceptionCaseService;
    private final BotPayloadBuilder botPayloadBuilder;
    private final TransactionTemplate transactionTemplate;

    @Value("${bot.enabled:false}")
    private boolean enabled;

    @Value("${bot.allowed-user-ids:}")
    private String allowedUserIdsValue;

    @Value("${bot.reply-token:}")
    private String replyToken;

    @Value("${bot.debounce-seconds:10}")
    private long debounceSeconds;

    @Value("${bot.per-user-hourly-limit:20}")
    private long perUserHourlyLimit;

    @Value("${bot.daily-limit:300}")
    private long dailyLimit;

    private Set<UUID> allowedUserIds = Set.of();

    @PostConstruct
    void init() {
        Set<UUID> ids = new HashSet<>();
        Arrays.stream(allowedUserIdsValue.split(",")).map(String::trim).filter(StringUtils::hasText).forEach(value -> {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                log.warn("bot.allowed-user-ids에 UUID가 아닌 값이 있어 건너뜁니다.");
            }
        });
        allowedUserIds = Set.copyOf(ids);
    }

    /** 서버 마스터 스위치(bot.enabled). 꺼져 있으면 스케줄러가 DB도 보지 않는다. */
    public boolean isMasterEnabled() {
        return enabled;
    }

    /** 마스터 스위치·답변 토큰·관리자 전역 토글이 모두 켜져 있는지. Slack 신호 주소는 없어도 된다(pull은 동작). */
    public boolean isActive() {
        return enabled && StringUtils.hasText(replyToken) && isToggleOn();
    }

    private boolean isToggleOn() {
        return botSettingRepository.findById(BotSetting.ID).map(BotSetting::isEnabled).orElse(true);
    }

    /** 고객 메시지 1건을 대기열에 넣는다(BotDispatchListener가 단일 스레드로 호출). */
    @Transactional
    public void enqueue(UUID messageId) {
        if (!isActive()) {
            return;
        }
        ChatService.InquiryMessage message = chatService.findCustomerInquiryMessage(messageId).orElse(null);
        if (message == null || message.botPaused() || !isTargetCustomer(message.customerId())
                || botDispatchRepository.existsByMessageId(messageId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sendAfter = now.plusSeconds(debounceSeconds);
        // ponytail: 같은 메시지 이벤트가 디바운스로 다음 메시지에 밀린 뒤 늦게 다시 오면 message_id가 이전 메시지로 돌아갈 수 있다.
        //  이벤트 중복은 같은 커밋 직후에만 생겨 실사용 영향이 없다고 본다. 생기면 메시지 created_at 비교를 추가.
        Optional<BotDispatch> pending = botDispatchRepository.findFirstByConversationIdAndStatus(
                message.roomId(), BotDispatchStatus.PENDING);
        if (pending.isPresent()) {
            pending.get().debounce(messageId, sendAfter);
            return;
        }
        if (isOverLimit(message.customerId(), now)) {
            BotDispatch limited = botDispatchRepository.save(BotDispatch.handedOffWithoutSending(
                    message.roomId(), messageId, message.customerId(), now, LIMIT_REASON));
            handOff(limited, LIMIT_REASON, true);
            return;
        }
        // 방당 PENDING 1건 부분 유니크와 겹치면(만료 처리가 같은 방 LEASED를 되돌린 순간) 여기서 바로 터지게 flush한다.
        // BotDispatchListener가 새 트랜잭션으로 한 번 더 부르면 그 PENDING을 찾아 디바운스로 묶는다.
        botDispatchRepository.saveAndFlush(BotDispatch.pending(message.roomId(), messageId, message.customerId(), sendAfter));
    }

    private boolean isTargetCustomer(UUID customerId) {
        if (allowedUserIds.contains(customerId)) {
            return true;
        }
        User user = userService.findUsersByIds(List.of(customerId)).get(customerId);
        return user != null && user.isDemo();
    }

    private boolean isOverLimit(UUID customerId, LocalDateTime now) {
        return botDispatchRepository.countByCustomerIdAndCreatedAtGreaterThanEqual(customerId, now.minusHours(1))
                >= perUserHourlyLimit
                || botDispatchRepository.countByCreatedAtGreaterThanEqual(now.toLocalDate().atStartOfDay()) >= dailyLimit;
    }

    /**
     * 대기 문의 조회(GET /api/bot/pending). 조회 가능한 PENDING을 limit(1~20)건 lease하고 본인 데이터로 본문을 만든다.
     * 봇이 비활성(토글 OFF 등)이면 빈 목록 — 남은 PENDING은 미처리 타임아웃이 담당자에게 넘긴다.
     */
    public BotPendingResponse listPending(int limit) {
        if (!isActive()) {
            return new BotPendingResponse(List.of());
        }
        int size = Math.max(1, Math.min(MAX_PENDING_LIMIT, limit));
        List<Map<String, Object>> items = new ArrayList<>();
        for (BotDispatch dispatch : leaseDue(size, LocalDateTime.now())) {
            try {
                items.add(botPayloadBuilder.build(dispatch));
            } catch (RuntimeException e) {
                // 고객이 메시지를 지운 경우 등. 다시 가져가도 같으니 바로 FAILED + 이관(한 건 때문에 나머지를 막지 않는다).
                log.warn("그록봇 대기 문의 본문 생성 실패: dispatchId={}, error={}", dispatch.getId(), e.getClass().getSimpleName());
                failLeased(dispatch.getId());
            }
        }
        return new BotPendingResponse(items);
    }

    // lease는 짧은 트랜잭션으로 커밋하고 본문은 그 뒤에 만든다(조회 서비스 예외가 lease를 되돌리지 않게).
    private List<BotDispatch> leaseDue(int limit, LocalDateTime now) {
        List<BotDispatch> leased = transactionTemplate.execute(status -> {
            List<BotDispatch> result = new ArrayList<>();
            for (String id : botDispatchRepository.lockDueIds(now, limit)) {
                BotDispatch dispatch = botDispatchRepository.findById(UUID.fromString(id)).orElseThrow();
                // 반환 직전 다시 확인: 그 사이 담당자가 답했거나 대상에서 빠졌으면 돌려주지 않는다.
                if (chatService.isBotPaused(dispatch.getConversationId())) {
                    dispatch.markHandedOff(PAUSED_REASON);
                    continue;
                }
                if (!isTargetCustomer(dispatch.getCustomerId())) {
                    dispatch.markHandedOff(NOT_TARGET_REASON);
                    handOff(dispatch, NOT_TARGET_REASON, true);
                    continue;
                }
                dispatch.lease(now.plus(LEASE));
                result.add(dispatch);
            }
            return result;
        });
        return leased == null ? List.of() : leased;
    }

    private void failLeased(UUID dispatchId) {
        transactionTemplate.executeWithoutResult(status -> {
            BotDispatch dispatch = botDispatchRepository.findById(dispatchId).orElseThrow();
            if (dispatch.getStatus() != BotDispatchStatus.LEASED) {
                return;
            }
            dispatch.markFailed(PAYLOAD_FAILED_REASON);
            handOff(dispatch, PAYLOAD_FAILED_REASON, true);
        });
    }

    /**
     * 기한 지난 LEASED: 같은 방에 더 새 dispatch가 있으면(대체됨, 그 답변은 어차피 409) 안내 없이 닫고,
     * 담당자 응대 중이면 닫기만, lease 3회 소진이면 FAILED + 이관, 아니면 다시 PENDING.
     */
    @Transactional
    public void expireLeases(LocalDateTime now) {
        for (String id : botDispatchRepository.lockExpiredLeaseIds(now)) {
            BotDispatch dispatch = botDispatchRepository.findById(UUID.fromString(id)).orElseThrow();
            if (botDispatchRepository.existsByConversationIdAndCreatedAtAfter(
                    dispatch.getConversationId(), dispatch.getCreatedAt())) {
                dispatch.markHandedOff(SUPERSEDED_REASON);
            } else if (chatService.isBotPaused(dispatch.getConversationId())) {
                dispatch.markHandedOff(PAUSED_REASON);
            } else if (dispatch.isLeaseExhausted()) {
                dispatch.markFailed(BotDispatch.LEASE_EXHAUSTED_REASON);
                handOff(dispatch, BotDispatch.LEASE_EXHAUSTED_REASON, true);
            } else {
                dispatch.requeue(now);
            }
        }
    }

    /**
     * 조회 가능해진 뒤(send_after) 5분 동안 아무도 가져가지 않은 PENDING(신호 유실·에이전트 정지)을 이관한다.
     * 관리자 전역 토글과 무관하게 돈다. 담당자 응대 중인 방은 이관·안내 없이 닫기만 한다.
     */
    @Transactional
    public void handOffUnclaimed(LocalDateTime now) {
        for (String id : botDispatchRepository.lockUnclaimedIds(now.minus(UNCLAIMED_TIMEOUT))) {
            BotDispatch dispatch = botDispatchRepository.findById(UUID.fromString(id)).orElseThrow();
            if (chatService.isBotPaused(dispatch.getConversationId())) {
                dispatch.markHandedOff(PAUSED_REASON);
                continue;
            }
            dispatch.markHandedOff(TIMEOUT_REASON);
            handOff(dispatch, TIMEOUT_REASON, true);
        }
    }

    /**
     * 담당자 이관(공통): 방 needsHuman, 이 방의 열린 CS 예외가 없으면 새로 연다.
     * notifyCustomer(미처리·lease 소진·한도 초과·대상 아님)면 고정 안내를 봇 이름으로 남긴다.
     * 이미 이관 중인 방, 담당자가 응대 중(botPaused)인 방엔 남기지 않는다.
     * dispatch 상태는 호출 측이 정한다.
     */
    @Transactional
    public void handOff(BotDispatch dispatch, String reason, boolean notifyCustomer) {
        UUID roomId = dispatch.getConversationId();
        boolean alreadyNeedsHuman = chatService.markNeedsHuman(roomId);
        String prefix = caseReasonPrefix(roomId);
        if (exceptionCaseService.listOpen(ExceptionCaseType.CS, null, prefix).isEmpty()) {
            exceptionCaseService.open(ExceptionCaseType.CS, null, null, null, prefix + " " + reason);
        }
        if (notifyCustomer && !alreadyNeedsHuman && !chatService.isBotPaused(roomId)) {
            chatService.postBotMessage(roomId, HANDOFF_NOTICE);
        }
    }

    /** CS 예외 사유 접두사 "문의방 {roomId}:". 재개(AdminChatService.resumeBot)도 이 접두사로 닫는다. */
    public static String caseReasonPrefix(UUID roomId) {
        return "문의방 " + roomId + ":";
    }

    @Transactional(readOnly = true)
    public BotSettingResponse getSettings() {
        return new BotSettingResponse(isToggleOn(), isActive());
    }

    @Transactional
    public BotSettingResponse updateSettings(boolean toggle, UUID adminId) {
        BotSetting setting = botSettingRepository.findById(BotSetting.ID)
                .orElseGet(() -> botSettingRepository.save(BotSetting.initial()));
        setting.change(toggle, adminId);
        return new BotSettingResponse(setting.isEnabled(), isActive());
    }
}
