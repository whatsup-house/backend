package com.whatsuphouse.backend.domain.payment.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.dto.request.DepositNotificationLinkRequest;
import com.whatsuphouse.backend.domain.payment.dto.response.DepositNotificationResponse;
import com.whatsuphouse.backend.domain.payment.entity.DepositNotification;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import com.whatsuphouse.backend.domain.payment.enums.DepositSource;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import com.whatsuphouse.backend.domain.payment.dto.request.DemoDepositRequest;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import com.whatsuphouse.backend.domain.payment.repository.DepositNotificationRepository;
import com.whatsuphouse.backend.domain.payment.repository.DepositRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.whatsuphouse.backend.domain.notification.event.AdminDepositNotifiedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DepositReconciliationServiceTest {

    @Mock private DepositNotificationRepository depositNotificationRepository;
    @Mock private DepositService depositService;
    @Mock private TicketService ticketService;
    @Mock private ApplicationService applicationService;
    @Mock private ExceptionCaseService exceptionCaseService;
    // 실제 트랜잭션 대신 콜백만 실행된다(커밋·롤백 호출은 검증용).
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DepositRepository depositRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DepositReconciliationService service;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 20, 13, 19, 30);
    private final UUID passId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(depositNotificationRepository.findByTextHash(anyString())).thenReturn(Optional.empty());
        lenient().when(depositNotificationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static String alert(String name, String amount) {
        return "[입금] " + name + "        " + amount + "원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30";
    }

    private void candidates(List<PendingPayment> passes, List<PendingPayment> deposits, List<PendingPayment> regular) {
        given(ticketService.listPendingPasses()).willReturn(passes);
        given(depositService.listPendingStandalone()).willReturn(deposits);
        given(applicationService.listPendingRegularPayments()).willReturn(regular);
    }

    private ExceptionCase exceptionCase() {
        ExceptionCase exceptionCase = ExceptionCase.open(ExceptionCaseType.PAYMENT, null, null, null, "x");
        ReflectionTestUtils.setField(exceptionCase, "id", UUID.randomUUID());
        return exceptionCase;
    }

    private DepositNotification saved() {
        ArgumentCaptor<DepositNotification> captor = ArgumentCaptor.forClass(DepositNotification.class);
        then(depositNotificationRepository).should(times(1)).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("후보가 정확히 1건이면 이용권을 정산하고 MATCHED로 기록한다")
    void reconcile_singleMatch_settlesPass() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(), List.of());

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MATCHED);
        assertThat(result.getParsedName()).isEqualTo("한지형");
        assertThat(result.getParsedAmount()).isEqualTo(9_900);
        assertThat(result.getLinkedApplicationId()).isEqualTo(applicationId);
        then(ticketService).should().settlePass(passId, "WEBHOOK");
        then(depositService).should(never()).markPaid(any());
        DepositNotification notification = saved();
        assertThat(notification.getTargetType()).isEqualTo(PaymentTargetType.TICKET_PASS);
        assertThat(notification.getTargetId()).isEqualTo(passId);
        assertThat(notification.getExceptionCaseId()).isNull();
    }

    @Test
    @DisplayName("후보가 없으면 PAYMENT 예외함을 열고 NO_CANDIDATE로 기록한다")
    void reconcile_noCandidate_opensException() {
        // given
        candidates(List.of(), List.of(), List.of());
        ExceptionCase exceptionCase = exceptionCase();
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(), isNull(),
                startsWith("입금 대사 실패: "))).willReturn(exceptionCase);

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        assertThat(result.getLinkedApplicationId()).isNull();
        assertThat(saved().getExceptionCaseId()).isEqualTo(exceptionCase.getId());
        then(ticketService).should(never()).settlePass(any(), anyString());
    }

    @Test
    @DisplayName("같은 이름·같은 금액 후보가 2건이면 자동 처리하지 않고 예외함")
    void reconcile_twoSameNameSameAmount_opensException() {
        // given
        candidates(List.of(
                new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900),
                new PendingPayment(PaymentTargetType.TICKET_PASS, UUID.randomUUID(), UUID.randomUUID(), "한지형", 9_900)),
                List.of(), List.of());
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(), isNull(),
                startsWith("입금 대사 실패: "))).willReturn(exceptionCase());

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MULTIPLE_CANDIDATES);
        then(ticketService).should(never()).settlePass(any(), anyString());
    }

    @Test
    @DisplayName("1회권 + 같은 신청 보증금은 합산 59,900 후보 1건이고, 보증금 입금 처리 후 이용권을 정산한다")
    void reconcile_bundledPassAndDeposit_singleCandidate() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(new PendingPayment(PaymentTargetType.DEPOSIT, UUID.randomUUID(), applicationId, "한지형", 50_000)),
                List.of());
        given(depositService.markPaid(applicationId)).willReturn(true);

        // when
        List<PendingPayment> targets = service.listPendingTargets();
        DepositNotificationResponse result = service.ingest(alert("한지형", "59,900"), DepositSource.SIMULATOR, now);

        // then
        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.type()).isEqualTo(PaymentTargetType.TICKET_PASS);
            assertThat(target.amount()).isEqualTo(59_900);
        });
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MATCHED);
        InOrder order = inOrder(depositService, ticketService);
        order.verify(depositService).markPaid(applicationId);
        order.verify(ticketService).settlePass(passId, "SIMULATOR");
    }

    @Test
    @DisplayName("합산 대상 보증금은 단독 50,000 후보에서 빠진다(보증금만 입금하면 후보 없음)")
    void reconcile_bundledDepositAlone_noCandidate() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(new PendingPayment(PaymentTargetType.DEPOSIT, UUID.randomUUID(), applicationId, "한지형", 50_000)),
                List.of());
        given(exceptionCaseService.open(any(), any(), any(), any(), anyString())).willReturn(exceptionCase());

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "50,000"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        then(depositService).should(never()).markPaid(any());
    }

    @Test
    @DisplayName("은행이 자른 이름(payerName의 앞부분)도 후보가 1건이면 매칭한다")
    void reconcile_prefixName_matchesWhenUnique() {
        // given
        UUID depositId = UUID.randomUUID();
        candidates(List.of(), List.of(new PendingPayment(PaymentTargetType.DEPOSIT, depositId, applicationId,
                "남궁민수아", 50_000)), List.of());
        given(depositService.markPaid(applicationId)).willReturn(true);

        // when
        DepositNotificationResponse result = service.ingest(alert("남궁민수", "50,000"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MATCHED);
        then(depositService).should().markPaid(applicationId);
        assertThat(saved().getTargetId()).isEqualTo(depositId);
    }

    @Test
    @DisplayName("일반 모임 결제 대기 건이 매칭되면 기존 입금 확인 경로로 결제 완료한다")
    void reconcile_regularApplication_confirmsPayment() {
        // given
        candidates(List.of(), List.of(), List.of(new PendingPayment(PaymentTargetType.REGULAR_APPLICATION,
                applicationId, applicationId, "이영희", 30_000)));
        given(applicationService.confirmRegularPaymentBySystem(applicationId)).willReturn(true);

        // when
        DepositNotificationResponse result = service.ingest(alert("이영희", "30,000"), DepositSource.IOS_SHORTCUT, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MATCHED);
        then(applicationService).should().confirmRegularPaymentBySystem(applicationId);
    }

    @Test
    @DisplayName("출금 알림은 기록만 하고 무시(IGNORED), 예외함 없음")
    void ingest_withdrawal_ignored() {
        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900").replace("[입금]", "[출금]"),
                DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.IGNORED);
        then(exceptionCaseService).should(never()).open(any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("입금 알림인데 형식을 못 읽으면(문자 원문 등) PARSE_FAILED + 예외함")
    void ingest_unparseable_parseFailed() {
        // given
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(), isNull(),
                startsWith("입금 대사 실패: "))).willReturn(exceptionCase());

        // when
        DepositNotificationResponse result = service.ingest("[Web발신] 우리 입금 9,900원 한지형", DepositSource.IOS_SHORTCUT, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.PARSE_FAILED);
    }

    @Test
    @DisplayName("같은 원문(해시)이면 다시 처리하지 않고 기존 기록을 돌려준다")
    void ingest_duplicateHash_returnsExisting() {
        // given
        String raw = alert("한지형", "9,900");
        String hash = DepositReconciliationService.hash(raw);
        DepositNotification existing = DepositNotification.receive(raw, hash, DepositSource.MACRODROID, now);
        given(depositNotificationRepository.findByTextHash(hash)).willReturn(Optional.of(existing));

        // when
        DepositNotificationResponse result = service.ingest(raw, DepositSource.MACRODROID, now);

        // then
        assertThat(result.getRawText()).isEqualTo(raw);
        then(depositNotificationRepository).should(never()).saveAndFlush(any());
        then(ticketService).should(never()).listPendingPasses();
    }

    @Test
    @DisplayName("예외 건 수동 연결: 같은 정산 경로(합산 보증금 먼저)로 처리하고 MATCHED, 예외 건은 닫는다")
    void linkNotification_settlesAndResolvesException() {
        // given
        UUID notificationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID exceptionCaseId = UUID.randomUUID();
        DepositNotification notification = DepositNotification.receive("[입금] 한지 59,900원 ", "h",
                DepositSource.MACRODROID, now);
        notification.parsed("한지", 59_900);
        notification.fail(DepositMatchResult.NO_CANDIDATE, exceptionCaseId);
        given(depositNotificationRepository.findByIdForUpdate(notificationId)).willReturn(Optional.of(notification));
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(new PendingPayment(PaymentTargetType.DEPOSIT, UUID.randomUUID(), applicationId, "한지형", 50_000)),
                List.of());
        DepositNotificationLinkRequest request = new DepositNotificationLinkRequest();
        ReflectionTestUtils.setField(request, "targetType", PaymentTargetType.TICKET_PASS);
        ReflectionTestUtils.setField(request, "targetId", passId);
        given(depositService.markPaid(applicationId)).willReturn(true);

        // when
        service.linkNotification(notificationId, request, adminId);

        // then
        InOrder order = inOrder(depositService, ticketService);
        order.verify(depositService).markPaid(applicationId);
        order.verify(ticketService).settlePass(passId, "ADMIN:" + adminId);
        assertThat(notification.getResult()).isEqualTo(DepositMatchResult.MATCHED);
        assertThat(notification.getApplicationId()).isEqualTo(applicationId);
        then(exceptionCaseService).should().resolveIfOpen(eq(exceptionCaseId), eq(adminId), anyString());
    }

    @Test
    @DisplayName("정산 중 경쟁으로 이용권이 이미 처리됐으면 롤백 후 알림을 따로 기록하고 예외함을 연다")
    void ingest_settleFails_recordsNotificationAndException() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(), List.of());
        willThrow(new CustomException(ErrorCode.TICKET_ALREADY_PROCESSED)).given(ticketService).settlePass(passId, "WEBHOOK");
        ExceptionCase exceptionCase = exceptionCase();
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(), eq(applicationId),
                startsWith("입금 대사 실패: "))).willReturn(exceptionCase);

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        then(transactionManager).should().rollback(any());
        ArgumentCaptor<DepositNotification> captor = ArgumentCaptor.forClass(DepositNotification.class);
        then(depositNotificationRepository).should(times(2)).saveAndFlush(captor.capture());
        DepositNotification recorded = captor.getAllValues().get(1);
        assertThat(recorded.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        assertThat(recorded.getExceptionCaseId()).isEqualTo(exceptionCase.getId());
        assertThat(recorded.getTargetId()).isNull();
    }

    private void stubOpenCase(UUID applicationId) {
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(),
                applicationId == null ? isNull() : eq(applicationId), startsWith("입금 대사 실패: ")))
                .willReturn(exceptionCase());
    }

    private DepositNotification secondSaved() {
        ArgumentCaptor<DepositNotification> captor = ArgumentCaptor.forClass(DepositNotification.class);
        then(depositNotificationRepository).should(times(2)).saveAndFlush(captor.capture());
        return captor.getAllValues().get(1);
    }

    @Test
    @DisplayName("동시 입금 등으로 보증금이 이미 입금 확인됐으면 두 번째 알림은 MATCHED가 아니라 예외함(중복 정산 없음)")
    void reconcile_depositAlreadyPaid_exceptionBox() {
        // given
        candidates(List.of(), List.of(new PendingPayment(PaymentTargetType.DEPOSIT, UUID.randomUUID(), applicationId,
                "한지형", 50_000)), List.of());
        given(depositService.markPaid(applicationId)).willReturn(false);
        stubOpenCase(applicationId);

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "50,000"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        then(transactionManager).should().rollback(any());
        DepositNotification recorded = secondSaved();
        assertThat(recorded.getTargetId()).isNull();
        assertThat(recorded.getExceptionCaseId()).isNotNull();
    }

    @Test
    @DisplayName("일반 모임 신청이 이미 입금 확인됐으면 두 번째 알림은 예외함(중복 정산 없음)")
    void reconcile_regularAlreadyConfirmed_exceptionBox() {
        // given
        candidates(List.of(), List.of(), List.of(new PendingPayment(PaymentTargetType.REGULAR_APPLICATION,
                applicationId, applicationId, "이영희", 30_000)));
        given(applicationService.confirmRegularPaymentBySystem(applicationId)).willReturn(false);
        stubOpenCase(applicationId);

        // when
        DepositNotificationResponse result = service.ingest(alert("이영희", "30,000"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        assertThat(secondSaved().getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
    }

    @Test
    @DisplayName("합산 보증금이 이미 입금 확인됐으면 이용권도 정산하지 않고 예외함")
    void reconcile_bundledDepositAlreadyPaid_doesNotSettlePass() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(new PendingPayment(PaymentTargetType.DEPOSIT, UUID.randomUUID(), applicationId, "한지형", 50_000)),
                List.of());
        given(depositService.markPaid(applicationId)).willReturn(false);
        stubOpenCase(applicationId);

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "59,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        then(ticketService).should(never()).settlePass(any(), anyString());
    }

    @Test
    @DisplayName("입금자명이 비면(공백만) 모든 후보에 앞부분 일치하므로 파싱 실패로 예외함")
    void ingest_blankName_parseFailed() {
        // given
        stubOpenCase(null);

        // when
        DepositNotificationResponse result = service.ingest(
                "[입금]   9,900원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30", DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.PARSE_FAILED);
        then(ticketService).should(never()).listPendingPasses();
    }

    @Test
    @DisplayName("한 글자 입금자명은 후보가 1건이어도 자동 처리하지 않고 예외함")
    void ingest_oneCharName_exceptionBox() {
        // given
        stubOpenCase(null);

        // when
        DepositNotificationResponse result = service.ingest(alert("김", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        assertThat(result.getParsedName()).isEqualTo("김");
        then(ticketService).should(never()).settlePass(any(), anyString());
        then(ticketService).should(never()).listPendingPasses();
    }

    @Test
    @DisplayName("정확히 같은 이름 후보 + 그 이름으로 시작하는 후보가 함께 있으면 MULTIPLE_CANDIDATES")
    void reconcile_exactAndPrefixCandidates_multiple() {
        // given
        candidates(List.of(
                new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900),
                new PendingPayment(PaymentTargetType.TICKET_PASS, UUID.randomUUID(), UUID.randomUUID(), "한지형수", 9_900)),
                List.of(), List.of());
        stubOpenCase(null);

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MULTIPLE_CANDIDATES);
        then(ticketService).should(never()).settlePass(any(), anyString());
    }

    @Test
    @DisplayName("무결성 오류인데 같은 해시 행이 없으면(다른 원인) 다시 던지지 않고 알림·예외 건을 남긴다")
    void ingest_integrityViolationWithoutWinner_recordsNotification() {
        // given
        given(depositNotificationRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("x"))
                .willAnswer(inv -> inv.getArgument(0));
        stubOpenCase(null);

        // when
        DepositNotificationResponse result = service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.NO_CANDIDATE);
        assertThat(secondSaved().getExceptionCaseId()).isNotNull();
    }

    private DepositNotification failedNotification(DepositMatchResult result, UUID exceptionCaseId) {
        DepositNotification notification = DepositNotification.receive(alert("한지형", "59,900"), "h",
                DepositSource.MACRODROID, now);
        notification.parsed("한지형", 59_900);
        notification.fail(result, exceptionCaseId);
        return notification;
    }

    private DepositNotificationLinkRequest linkRequest(boolean force) {
        DepositNotificationLinkRequest request = new DepositNotificationLinkRequest();
        ReflectionTestUtils.setField(request, "targetType", PaymentTargetType.TICKET_PASS);
        ReflectionTestUtils.setField(request, "targetId", passId);
        ReflectionTestUtils.setField(request, "force", force);
        return request;
    }

    @Test
    @DisplayName("수동 연결: IGNORED·MATCHED 행은 409, 정산하지 않는다")
    void linkNotification_ignoredOrMatched_409() {
        // given
        UUID ignoredId = UUID.randomUUID();
        UUID matchedId = UUID.randomUUID();
        DepositNotification ignored = DepositNotification.receive("[출금] x", "h1", DepositSource.MACRODROID, now);
        DepositNotification matched = failedNotification(DepositMatchResult.NO_CANDIDATE, null);
        matched.match(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 59_900));
        given(depositNotificationRepository.findByIdForUpdate(ignoredId)).willReturn(Optional.of(ignored));
        given(depositNotificationRepository.findByIdForUpdate(matchedId)).willReturn(Optional.of(matched));

        // when & then
        assertThatThrownBy(() -> service.linkNotification(ignoredId, linkRequest(false), UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEPOSIT_NOTIFICATION_NOT_LINKABLE);
        assertThatThrownBy(() -> service.linkNotification(matchedId, linkRequest(false), UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEPOSIT_NOTIFICATION_NOT_LINKABLE);
        then(ticketService).should(never()).settlePass(any(), anyString());
    }

    @Test
    @DisplayName("수동 연결: 입금액이 대상 금액과 다르면 force 없이는 409, force면 연결한다")
    void linkNotification_amountMismatch_requiresForce() {
        // given
        UUID notificationId = UUID.randomUUID();
        DepositNotification notification = failedNotification(DepositMatchResult.MULTIPLE_CANDIDATES, null);
        given(depositNotificationRepository.findByIdForUpdate(notificationId)).willReturn(Optional.of(notification));
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(), List.of());

        // when & then
        assertThatThrownBy(() -> service.linkNotification(notificationId, linkRequest(false), UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEPOSIT_AMOUNT_MISMATCH);
        then(ticketService).should(never()).settlePass(any(), anyString());

        service.linkNotification(notificationId, linkRequest(true), UUID.randomUUID());
        then(ticketService).should().settlePass(eq(passId), startsWith("ADMIN:"));
        assertThat(notification.getResult()).isEqualTo(DepositMatchResult.MATCHED);
    }

    @Test
    @DisplayName("시뮬레이터 재클릭: 같은 건의 열린 예외 건이 있으면 새 알림·예외 건을 만들지 않고 기존 결과를 돌려준다")
    void simulateDeposit_openException_returnsExisting() {
        // given
        UUID userId = UUID.randomUUID();
        User demo = User.builder().email("d@example.com").password("p").name("한지형").build();
        demo.markDemo();
        given(userService.findUser(userId)).willReturn(demo);
        given(depositRepository.existsByApplication_IdAndApplication_User_IdAndStatus(applicationId, userId, DepositStatus.PENDING))
                .willReturn(true);
        candidates(List.of(), List.of(new PendingPayment(PaymentTargetType.DEPOSIT, UUID.randomUUID(), applicationId,
                "한지형", 50_000)), List.of());
        UUID exceptionCaseId = UUID.randomUUID();
        DepositNotification previous = failedNotification(DepositMatchResult.MULTIPLE_CANDIDATES, exceptionCaseId);
        given(depositNotificationRepository.findFirstBySourceAndParsedNameAndParsedAmountAndExceptionCaseIdIsNotNullOrderByCreatedAtDesc(
                DepositSource.SIMULATOR, "한지형", 50_000)).willReturn(Optional.of(previous));
        given(exceptionCaseService.isOpen(exceptionCaseId)).willReturn(true);
        DemoDepositRequest request = new DemoDepositRequest();
        ReflectionTestUtils.setField(request, "applicationId", applicationId);

        // when
        DepositNotificationResponse result = service.simulateDeposit(userId, request);

        // then
        assertThat(result.getResult()).isEqualTo(DepositMatchResult.MULTIPLE_CANDIDATES);
        then(depositNotificationRepository).should(never()).saveAndFlush(any());
        then(exceptionCaseService).should(never()).open(any(), any(), any(), any(), anyString());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    // ── 관리자 알림 이벤트 (KAN-416) ─────────────────────────────────────────

    private AdminDepositNotifiedEvent publishedAdminEvent() {
        ArgumentCaptor<AdminDepositNotifiedEvent> captor = ArgumentCaptor.forClass(AdminDepositNotifiedEvent.class);
        then(eventPublisher).should(times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("자동 매칭되면 관리자 알림 이벤트 1건(matched, 입금자명·금액만)")
    void ingest_matched_publishesAdminEvent() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(), List.of());

        // when
        service.ingest(alert("한지형", "9,900"), DepositSource.IOS_SHORTCUT, now);

        // then
        AdminDepositNotifiedEvent event = publishedAdminEvent();
        assertThat(event.isMatched()).isTrue();
        assertThat(event.isManual()).isFalse();
        assertThat(event.getPayerName()).isEqualTo("한지형");
        assertThat(event.getAmount()).isEqualTo(9_900);
    }

    @Test
    @DisplayName("예외함으로 가면(후보 없음) 관리자 알림 이벤트 1건(matched=false)")
    void ingest_noCandidate_publishesAdminExceptionEvent() {
        // given
        candidates(List.of(), List.of(), List.of());
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(), isNull(),
                startsWith("입금 대사 실패: "))).willReturn(exceptionCase());

        // when
        service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        AdminDepositNotifiedEvent event = publishedAdminEvent();
        assertThat(event.isMatched()).isFalse();
        assertThat(event.getPayerName()).isEqualTo("한지형");
    }

    @Test
    @DisplayName("정산 실패로 롤백되면 실패 기록 쪽 예외 이벤트 1건만 나간다")
    void ingest_settleFails_publishesOnlyExceptionEvent() {
        // given
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(), List.of());
        willThrow(new CustomException(ErrorCode.TICKET_ALREADY_PROCESSED)).given(ticketService).settlePass(passId, "WEBHOOK");
        given(exceptionCaseService.open(eq(ExceptionCaseType.PAYMENT), isNull(), isNull(), eq(applicationId),
                startsWith("입금 대사 실패: "))).willReturn(exceptionCase());

        // when
        service.ingest(alert("한지형", "9,900"), DepositSource.MACRODROID, now);

        // then
        assertThat(publishedAdminEvent().isMatched()).isFalse();
    }

    @Test
    @DisplayName("출금(IGNORED)·같은 원문 재수신은 관리자 알림 이벤트를 내지 않는다")
    void ingest_ignoredOrDuplicate_noAdminEvent() {
        // given
        String raw = alert("한지형", "9,900");
        DepositNotification existing = DepositNotification.receive(raw, DepositReconciliationService.hash(raw),
                DepositSource.MACRODROID, now);
        existing.parsed("한지형", 9_900);
        existing.fail(DepositMatchResult.NO_CANDIDATE, UUID.randomUUID());
        given(depositNotificationRepository.findByTextHash(DepositReconciliationService.hash(raw)))
                .willReturn(Optional.of(existing));

        // when
        service.ingest(raw.replace("[입금]", "[출금]"), DepositSource.MACRODROID, now);
        service.ingest(raw, DepositSource.MACRODROID, now);

        // then
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("수동 연결도 관리자 알림 이벤트 1건(matched, manual)")
    void linkNotification_publishesManualEvent() {
        // given
        UUID notificationId = UUID.randomUUID();
        DepositNotification notification = DepositNotification.receive("[입금] 한지형 9,900원 ", "h",
                DepositSource.MACRODROID, now);
        notification.parsed("한지형", 9_900);
        notification.fail(DepositMatchResult.NO_CANDIDATE, null);
        given(depositNotificationRepository.findByIdForUpdate(notificationId)).willReturn(Optional.of(notification));
        candidates(List.of(new PendingPayment(PaymentTargetType.TICKET_PASS, passId, applicationId, "한지형", 9_900)),
                List.of(), List.of());
        DepositNotificationLinkRequest request = new DepositNotificationLinkRequest();
        ReflectionTestUtils.setField(request, "targetType", PaymentTargetType.TICKET_PASS);
        ReflectionTestUtils.setField(request, "targetId", passId);

        // when
        service.linkNotification(notificationId, request, UUID.randomUUID());

        // then
        AdminDepositNotifiedEvent event = publishedAdminEvent();
        assertThat(event.isMatched()).isTrue();
        assertThat(event.isManual()).isTrue();
    }
}
