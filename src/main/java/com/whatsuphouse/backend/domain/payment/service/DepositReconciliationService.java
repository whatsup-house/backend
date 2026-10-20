package com.whatsuphouse.backend.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.dto.request.DemoDepositRequest;
import com.whatsuphouse.backend.domain.payment.dto.request.DepositNotificationLinkRequest;
import com.whatsuphouse.backend.domain.payment.dto.request.DepositWebhookRequest;
import com.whatsuphouse.backend.domain.payment.dto.response.AdminDepositNotificationPageResponse;
import com.whatsuphouse.backend.domain.payment.dto.response.AdminDepositNotificationResponse;
import com.whatsuphouse.backend.domain.payment.dto.response.DepositNotificationResponse;
import com.whatsuphouse.backend.domain.payment.entity.DepositNotification;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import com.whatsuphouse.backend.domain.payment.enums.DepositSource;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.payment.repository.DepositNotificationRepository;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.payment.service.DepositTextParser.ParsedDeposit;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 입금 알림 자동 대사(설계 2·14, KAN-403). 원문 저장(같은 원문은 한 번) → 파싱 → 결제 대기 후보 중
 * 이름·금액이 맞는 건이 정확히 1건이면 결제 완료, 0건·2건 이상·파싱 실패면 예외함(PAYMENT).
 * 후보 = 이용권(같은 신청의 입금 대기 보증금은 금액 합산) ∪ 단독 보증금 ∪ 일반 모임 결제 대기.
 * 웹훅·심사용 시뮬레이터·운영자 수동 연결이 같은 정산 경로(settle)를 탄다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositReconciliationService {

    static final int MAX_WEBHOOK_BODY_BYTES = 2_048;
    private static final String CASE_PREFIX = "입금 대사 실패: ";
    private static final DateTimeFormatter ALERT_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DepositNotificationRepository depositNotificationRepository;
    private final DepositRepository depositRepository;
    private final DepositService depositService;
    private final TicketService ticketService;
    private final ApplicationService applicationService;
    private final ExceptionCaseService exceptionCaseService;
    private final UserService userService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    // DEPOSIT_WEBHOOK_SECRET. 비어 있으면 웹훅은 항상 401.
    @Value("${payment.deposit-webhook.secret:}")
    private String webhookSecret;

    /** 결제 대기 후보 1건. withDeposit = 이용권 금액에 같은 신청의 입금 대기 보증금을 합산했다(정산 때 보증금도 입금 처리). */
    private record Target(PendingPayment payment, boolean withDeposit) {
    }

    /** 웹훅 수신. 시크릿 확인(상수 시간 비교) → 본문 2KB 제한 → 대사. 시크릿·원문은 로그에 남기지 않는다. */
    public DepositNotificationResponse receiveWebhook(String secret, InputStream body) {
        if (!isValidSecret(secret)) {
            throw new CustomException(ErrorCode.DEPOSIT_WEBHOOK_UNAUTHORIZED);
        }
        DepositWebhookRequest request = readWebhookBody(body);
        return ingest(request.getText(), toSource(request.getSource()), toReceivedAt(request.getReceivedAt()));
    }

    /**
     * 알림 1건 처리. 같은 원문(해시)이면 다시 처리하지 않고 기존 결과를 돌려준다.
     * 정산이 실패하면(경쟁으로 이미 처리됨, 정원 초과 등) 정산은 통째로 롤백하고 알림과 예외 건만 새 트랜잭션으로 남긴다.
     * 정산 서비스의 예외가 트랜잭션을 rollback-only로 만들기 때문에 같은 트랜잭션 안에서 잡지 않는다.
     */
    public DepositNotificationResponse ingest(String rawText, DepositSource source, LocalDateTime receivedAt) {
        String raw = rawText.strip();
        String hash = hash(raw);
        Optional<DepositNotification> existing = depositNotificationRepository.findByTextHash(hash);
        if (existing.isPresent()) {
            return DepositNotificationResponse.from(existing.get());
        }
        String actor = source == DepositSource.SIMULATOR ? "SIMULATOR" : "WEBHOOK";
        AtomicReference<Target> attempted = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        DepositNotification notification;
        try {
            notification = transaction.execute(status -> reconcile(raw, hash, source, receivedAt, actor, attempted));
        } catch (DataIntegrityViolationException e) {
            // 같은 원문이 동시에 들어와 다른 요청이 먼저 저장했다(UNIQUE text_hash).
            return depositNotificationRepository.findByTextHash(hash)
                    .map(DepositNotificationResponse::from)
                    .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            log.warn("입금 자동 정산 실패, 예외함으로 넘김: {}", reasonOf(e));
            notification = transaction.execute(status -> recordSettleFailure(raw, hash, source, receivedAt, attempted.get(), e));
        }
        log.info("입금 알림 처리: id={}, source={}, result={}", notification.getId(), source, notification.getResult());
        return DepositNotificationResponse.from(notification);
    }

    /**
     * 심사용 입금 시뮬레이터. 데모 회원 본인의 결제 대기 건(이용권 = 보증금 합산 금액, 또는 보증금만)으로
     * 은행 푸시와 같은 형식의 원문을 만들어 웹훅과 같은 대사 경로에 넣는다.
     */
    public DepositNotificationResponse simulateDeposit(UUID userId, DemoDepositRequest request) {
        if (!userService.findUser(userId).isDemo()) {
            throw new CustomException(ErrorCode.DEMO_DEPOSIT_FORBIDDEN);
        }
        if ((request.getPassId() == null) == (request.getApplicationId() == null)) {
            throw new CustomException(ErrorCode.DEMO_DEPOSIT_TARGET_INVALID);
        }
        PendingPayment target = request.getPassId() != null
                ? findOwnPassTarget(userId, request.getPassId())
                : findOwnDepositTarget(userId, request.getApplicationId());
        LocalDateTime now = LocalDateTime.now();
        // 잔액은 매번 달라 원문(해시)이 겹치지 않는다.
        String raw = String.format(Locale.ROOT, "[입금] %s        %,d원 1002-000-000***계좌 잔액 %,d원 %s",
                target.payerName(), target.amount(), ThreadLocalRandom.current().nextInt(1_000_000, 100_000_000),
                now.format(ALERT_TIME));
        return ingest(raw, DepositSource.SIMULATOR, now);
    }

    /** 운영자 수동 연결용 후보 목록. 자동 대사와 같은 후보(합산 적용). */
    @Transactional(readOnly = true)
    public List<PendingPayment> listPendingTargets() {
        return buildTargets().stream().map(Target::payment).toList();
    }

    @Transactional(readOnly = true)
    public AdminDepositNotificationPageResponse listNotifications(DepositMatchResult result, int page, int size) {
        if (size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<DepositNotification> rows = result == null
                ? depositNotificationRepository.findAll(pageable)
                : depositNotificationRepository.findAllByResult(result, pageable);
        return AdminDepositNotificationPageResponse.from(rows.map(AdminDepositNotificationResponse::from));
    }

    /** 예외 건 알림을 결제 대기 건에 수동 연결. 자동 대사와 같은 정산 후 예외 건을 처리 완료로 닫는다. */
    @Transactional
    public AdminDepositNotificationResponse linkNotification(UUID id, DepositNotificationLinkRequest request, UUID adminId) {
        DepositNotification notification = depositNotificationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(ErrorCode.DEPOSIT_NOTIFICATION_NOT_FOUND));
        if (notification.getResult() == DepositMatchResult.MATCHED) {
            throw new CustomException(ErrorCode.DEPOSIT_NOTIFICATION_ALREADY_MATCHED);
        }
        Target target = buildTargets().stream()
                .filter(candidate -> candidate.payment().type() == request.getTargetType()
                        && candidate.payment().targetId().equals(request.getTargetId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_TARGET_NOT_FOUND));
        settle(target, "ADMIN:" + adminId);
        notification.match(target.payment());
        if (notification.getExceptionCaseId() != null) {
            exceptionCaseService.resolveIfOpen(notification.getExceptionCaseId(), adminId,
                    "입금 알림 수동 연결 (" + describe(target) + ")");
        }
        return AdminDepositNotificationResponse.from(notification);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);    // 모든 JVM이 SHA-256을 지원한다
        }
    }

    private DepositNotification reconcile(String raw, String hash, DepositSource source, LocalDateTime receivedAt,
                                          String actor, AtomicReference<Target> attempted) {
        // 먼저 저장·flush해 같은 원문의 동시 요청을 UNIQUE(text_hash)에서 직렬화한다.
        DepositNotification notification = depositNotificationRepository.saveAndFlush(
                DepositNotification.receive(raw, hash, source, receivedAt));
        if (!DepositTextParser.isDepositAlert(raw)) {
            return notification;    // IGNORED(출금 등)
        }
        Optional<ParsedDeposit> parsed = DepositTextParser.parse(raw);
        if (parsed.isEmpty()) {
            notification.fail(DepositMatchResult.PARSE_FAILED, openCase(null, "입금 알림 형식을 읽지 못했습니다(파싱 실패)."));
            return notification;
        }
        String name = parsed.get().name();
        int amount = parsed.get().amount();
        notification.parsed(name, amount);
        List<Target> matches = buildTargets().stream()
                .filter(target -> isMatch(target.payment(), name, amount))
                .toList();
        if (matches.size() == 1) {
            attempted.set(matches.get(0));
            settle(matches.get(0), actor);
            notification.match(matches.get(0).payment());
        } else if (matches.isEmpty()) {
            notification.fail(DepositMatchResult.NO_CANDIDATE,
                    openCase(null, "후보 없음 (" + name + " " + won(amount) + ")"));
        } else {
            notification.fail(DepositMatchResult.MULTIPLE_CANDIDATES, openCase(null, "후보 " + matches.size() + "건 ("
                    + name + " " + won(amount) + ") — " + matches.stream().map(this::describe).collect(Collectors.joining(", "))));
        }
        return notification;
    }

    private DepositNotification recordSettleFailure(String raw, String hash, DepositSource source, LocalDateTime receivedAt,
                                                    Target target, RuntimeException cause) {
        DepositNotification notification = depositNotificationRepository.saveAndFlush(
                DepositNotification.receive(raw, hash, source, receivedAt));
        DepositTextParser.parse(raw).ifPresent(parsed -> notification.parsed(parsed.name(), parsed.amount()));
        UUID applicationId = target != null ? target.payment().applicationId() : null;
        notification.fail(DepositMatchResult.NO_CANDIDATE, openCase(applicationId, "자동 정산 오류 " + reasonOf(cause)
                + " — 대상 " + (target != null ? describe(target) : "-")));
        return notification;
    }

    /** 결제 완료 처리. 운영자 버튼과 같은 서비스 메서드를 쓴다. */
    private void settle(Target target, String actor) {
        PendingPayment payment = target.payment();
        switch (payment.type()) {
            case TICKET_PASS -> {
                // 보증금 먼저: settlePass가 보증금 입금 여부로 신청 확정을 정한다.
                if (target.withDeposit()) {
                    depositService.markPaid(payment.applicationId());
                }
                ticketService.settlePass(payment.targetId(), actor);
            }
            case DEPOSIT -> depositService.markPaid(payment.applicationId());
            case REGULAR_APPLICATION -> applicationService.confirmRegularPaymentBySystem(payment.applicationId());
        }
    }

    private List<Target> buildTargets() {
        List<PendingPayment> deposits = depositService.listPendingStandalone();
        Map<UUID, PendingPayment> depositByApplication = deposits.stream()
                .collect(Collectors.toMap(PendingPayment::applicationId, Function.identity(), (first, second) -> first));
        Set<UUID> bundled = new HashSet<>();
        List<Target> targets = new ArrayList<>();
        for (PendingPayment pass : ticketService.listPendingPasses()) {
            PendingPayment deposit = pass.applicationId() != null ? depositByApplication.get(pass.applicationId()) : null;
            if (deposit != null && bundled.add(pass.applicationId())) {
                targets.add(new Target(new PendingPayment(PaymentTargetType.TICKET_PASS, pass.targetId(),
                        pass.applicationId(), pass.payerName(), pass.amount() + deposit.amount()), true));
            } else {
                targets.add(new Target(pass, false));
            }
        }
        deposits.stream()
                .filter(deposit -> !bundled.contains(deposit.applicationId()))
                .forEach(deposit -> targets.add(new Target(deposit, false)));
        applicationService.listPendingRegularPayments().forEach(regular -> targets.add(new Target(regular, false)));
        return targets;
    }

    // 금액이 같고, 알림 이름이 입금자명과 같거나 그 앞부분(은행이 긴 이름을 자른다).
    private static boolean isMatch(PendingPayment payment, String name, int amount) {
        return payment.amount() == amount && payment.payerName() != null && payment.payerName().startsWith(name);
    }

    private PendingPayment findOwnPassTarget(UUID userId, UUID passId) {
        boolean owned = ticketService.getMyTickets(userId).getPasses().stream()
                .anyMatch(pass -> pass.getId().equals(passId));
        return findTarget(owned, target -> target.type() == PaymentTargetType.TICKET_PASS
                && target.targetId().equals(passId));
    }

    private PendingPayment findOwnDepositTarget(UUID userId, UUID applicationId) {
        boolean owned = depositRepository.existsByApplication_IdAndApplication_User_IdAndStatus(
                applicationId, userId, DepositStatus.PENDING);
        return findTarget(owned, target -> target.type() == PaymentTargetType.DEPOSIT
                && applicationId.equals(target.applicationId()));
    }

    private PendingPayment findTarget(boolean owned, Predicate<PendingPayment> condition) {
        if (!owned) {
            throw new CustomException(ErrorCode.PAYMENT_TARGET_NOT_FOUND);
        }
        return listPendingTargets().stream()
                .filter(condition)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_TARGET_NOT_FOUND));
    }

    private UUID openCase(UUID applicationId, String detail) {
        return exceptionCaseService.open(ExceptionCaseType.PAYMENT, null, null, applicationId, CASE_PREFIX + detail).getId();
    }

    private String describe(Target target) {
        PendingPayment payment = target.payment();
        return payment.type() + " " + payment.payerName() + " " + won(payment.amount())
                + (payment.applicationId() != null ? " 신청 " + payment.applicationId() : "");
    }

    private static String won(int amount) {
        return String.format(Locale.ROOT, "%,d원", amount);
    }

    private static String reasonOf(RuntimeException e) {
        return e instanceof CustomException custom ? custom.getErrorCode().name() : e.getClass().getSimpleName();
    }

    private boolean isValidSecret(String secret) {
        return webhookSecret != null && !webhookSecret.isBlank() && secret != null
                && MessageDigest.isEqual(webhookSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }

    private DepositWebhookRequest readWebhookBody(InputStream body) {
        byte[] bytes;
        try {
            bytes = body.readNBytes(MAX_WEBHOOK_BODY_BYTES + 1);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_DEPOSIT_WEBHOOK);
        }
        if (bytes.length > MAX_WEBHOOK_BODY_BYTES) {
            throw new CustomException(ErrorCode.DEPOSIT_WEBHOOK_TOO_LARGE);
        }
        DepositWebhookRequest request;
        try {
            request = objectMapper.readValue(bytes, DepositWebhookRequest.class);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_DEPOSIT_WEBHOOK);   // 본문이 섞일 수 있어 원인 메시지는 남기지 않는다
        }
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_DEPOSIT_WEBHOOK);
        }
        return request;
    }

    // 모르는 값·SIMULATOR(서버 전용)는 MACRODROID로 본다.
    private static DepositSource toSource(String source) {
        if (source == null) {
            return DepositSource.MACRODROID;
        }
        try {
            DepositSource parsed = DepositSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
            return parsed == DepositSource.SIMULATOR ? DepositSource.MACRODROID : parsed;
        } catch (IllegalArgumentException e) {
            return DepositSource.MACRODROID;
        }
    }

    // ISO 로컬 시각 또는 오프셋 포함 시각. 못 읽으면 서버 수신 시각.
    private static LocalDateTime toReceivedAt(String receivedAt) {
        if (receivedAt == null || receivedAt.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(receivedAt.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(receivedAt.trim()).atZoneSameInstant(SEOUL).toLocalDateTime();
            } catch (DateTimeParseException e) {
                return LocalDateTime.now();
            }
        }
    }
}
