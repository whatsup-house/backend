package com.whatsuphouse.backend.domain.bot.service;

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
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 1:1 문의방 그록봇 자동응답 발송(KAN-406).
 * 대상: bot.enabled && 전역 토글 ON && (허용 목록 또는 데모 회원) && 방 botPaused=false 인 고객 본인 메시지.
 * 흐름: 메시지 → PENDING(같은 방 대기 건은 디바운스로 마지막 메시지 하나로 묶음) → 스케줄러가 발송 → SENT → 답변 API.
 * 실패·무응답·한도 초과는 담당자 이관(handOff). 봇은 데이터를 바꾸지 않는다(답변 게시·이관 표시만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotDispatchService {

    public static final String HANDOFF_NOTICE = "담당자에게 연결했어요. 곧 답변드릴게요.";
    static final String TIMEOUT_REASON = "응답 시간 초과";
    static final String LIMIT_REASON = "발송 한도 초과";
    private static final int SEND_BATCH = 20;

    private final BotDispatchRepository botDispatchRepository;
    private final BotSettingRepository botSettingRepository;
    private final ChatService chatService;
    private final UserService userService;
    private final ExceptionCaseService exceptionCaseService;
    private final BotPayloadBuilder botPayloadBuilder;
    private final BotWebhookClient botWebhookClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${bot.enabled:false}")
    private boolean enabled;

    @Value("${bot.allowed-user-ids:}")
    private String allowedUserIdsValue;

    @Value("${bot.reply-url:}")
    private String replyUrl;

    @Value("${bot.reply-token:}")
    private String replyToken;

    @Value("${bot.debounce-seconds:10}")
    private long debounceSeconds;

    @Value("${bot.reply-timeout-minutes:3}")
    private long replyTimeoutMinutes;

    @Value("${bot.per-user-hourly-limit:20}")
    private long perUserHourlyLimit;

    @Value("${bot.daily-limit:300}")
    private long dailyLimit;

    private boolean replyUrlAllowed;
    private Set<UUID> allowedUserIds = Set.of();

    @PostConstruct
    void init() {
        replyUrlAllowed = isWhatsupHttpsUrl(replyUrl);
        if (enabled && !replyUrlAllowed) {
            log.warn("bot.reply-url이 https://whatsup.house 또는 *.whatsup.house가 아니어서 그록봇을 비활성화합니다.");
        }
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

    /** 에이전트에게 알려 줄 답변 주소는 우리 도메인(https)만. 다른 호스트로 답변을 유도하는 설정 실수를 막는다. */
    public static boolean isWhatsupHttpsUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && host != null
                    && (host.equals("whatsup.house") || host.endsWith(".whatsup.house"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** 마스터 스위치·설정(웹훅 주소·키·답변 토큰·답변 주소)·관리자 전역 토글이 모두 켜져 있는지. */
    public boolean isActive() {
        return enabled && replyUrlAllowed && StringUtils.hasText(replyToken) && botWebhookClient.isConfigured()
                && isToggleOn();
    }

    private boolean isToggleOn() {
        return botSettingRepository.findById(BotSetting.ID).map(BotSetting::isEnabled).orElse(true);
    }

    /** 고객 메시지 1건을 발송 큐에 넣는다(BotDispatchListener가 단일 스레드로 호출). */
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
        botDispatchRepository.save(BotDispatch.pending(message.roomId(), messageId, message.customerId(), sendAfter));
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

    /** 발송할 때가 된 건을 1건씩 별도 트랜잭션(행 잠금 유지)으로 보낸다. */
    public void sendDue(LocalDateTime now) {
        for (int i = 0; i < SEND_BATCH; i++) {
            if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> sendNextDue(now)))) {
                return;
            }
        }
    }

    // 웹훅 응답(최대 15초)을 기다리는 동안 행 잠금을 쥔다: 그 사이 같은 방 새 메시지는 enqueue에서 커밋을 기다렸다가
    // SENT를 확인하고 새 PENDING을 만든다(보낸 건의 message_id가 바뀌지 않게).
    private boolean sendNextDue(LocalDateTime now) {
        List<String> ids = botDispatchRepository.lockNextDueId(now);
        if (ids.isEmpty()) {
            return false;
        }
        BotDispatch dispatch = botDispatchRepository.findById(UUID.fromString(ids.get(0))).orElseThrow();
        try {
            botWebhookClient.send(botPayloadBuilder.build(dispatch));
            dispatch.markSent(now);
        } catch (RuntimeException e) {
            dispatch.recordFailure(now, describe(e));
            log.warn("그록봇 웹훅 발송 실패: dispatchId={}, attempts={}, error={}",
                    dispatch.getId(), dispatch.getAttempts(), dispatch.getLastError());
            if (dispatch.isFailed()) {
                handOff(dispatch, BotDispatch.FAILED_REASON, true);
            }
        }
        return true;
    }

    // 상태 코드·예외 클래스만. 응답 본문·URL·키가 담길 수 있는 메시지는 남기지 않는다.
    private static String describe(RuntimeException e) {
        return e instanceof RestClientResponseException response
                ? "HTTP " + response.getStatusCode().value()
                : e.getClass().getSimpleName();
    }

    /** 답 없이 reply-timeout-minutes가 지난 SENT를 이관한다. 같은 방에 더 새 dispatch가 있으면 건너뛴다. */
    @Transactional
    public void handOffTimedOut(LocalDateTime now) {
        for (String id : botDispatchRepository.lockTimedOutIds(now.minusMinutes(replyTimeoutMinutes))) {
            BotDispatch dispatch = botDispatchRepository.findById(UUID.fromString(id)).orElseThrow();
            dispatch.markHandedOff(TIMEOUT_REASON);
            handOff(dispatch, TIMEOUT_REASON, true);
        }
    }

    /**
     * 담당자 이관(공통): 방 needsHuman, 이 방의 열린 CS 예외가 없으면 새로 연다.
     * notifyCustomer(웹훅 실패·무응답·한도 초과)면 고정 안내를 봇 이름으로 남긴다. 이미 이관 중인 방엔 반복하지 않는다.
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
        if (notifyCustomer && !alreadyNeedsHuman) {
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
