# 우연한 식탁 해커톤 갭 보강 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 계좌이체 입금 자동 대사, 1회권+기간제 멤버십, 취소·보증금 정책, AI CS, 심사용 데모를 실서비스(whatsup.house)에 2026-10-21 23:59 전까지 얹는다.

**Architecture:** 새 `domain/payment`(입금 알림·보증금·송금 요청)가 각 도메인이 내놓는 "결제 대기 건"을 모아 대사하고, 결제 완료는 기존 운영자 확인과 같은 서비스 메서드로 처리한다. 정책(마감·당일)은 순수 함수 하나로 판정한다. AI CS는 `ChatMessageCreatedEvent` 리스너 하나. FE는 기존 페이지에 카드·모달을 붙인다.

**Tech Stack:** Spring Boot 3.5 / Java 17 / JPA·Querydsl / Flyway / PostgreSQL / Redis, Next.js 16 / React 19 / TanStack Query / Tailwind 4.

**Spec:** `backend/docs/superpowers/specs/2026-10-19-dining-hackathon-gaps-design.md` (선행: `2026-09-29-dining-table-v2-design.md`)

## Global Constraints

- 가격: 1회권 9,900 / 멤버십 1개월 35,000 · 3개월 75,000 · 6개월 120,000. 보증금 50,000. 멤버십 매칭 실패 연장 7일.
- 마감 = 회차 날짜 D-1 00:00(= D-2 24:00, 목요일 회차면 화 24:00) = `applyDeadlineAt` = `matchRunAt` 기본값. 당일 = 회차 날짜 00:00 이후. 시간대 Asia/Seoul.
- 우리WON 푸시 정규식 `^\[입금\]\s+(.+?)\s+([\d,]+)원\s`. `[입금]` 아닌 알림은 IGNORED.
- 대사는 후보가 **정확히 1건**일 때만 자동 처리. 이름 비교: 같거나 `payerName.startsWith(알림이름)`.
- 타 도메인 Repository 직접 주입 금지(서비스·이벤트 경유). `remainingCount` 변경 시 `TicketTransaction` 기록 필수. 시스템 질문 키는 enum만.
- 커밋·PR에 AI·Claude·Co-Authored-By 문구 금지. 커밋 메시지 `feat: … (KAN-4xx)` 형식. 에픽 KAN-399, 각 태스크 제목 옆 키 사용.
- 시크릿(`DEPOSIT_WEBHOOK_SECRET`, `SLACK_SIGNAL_WEBHOOK_URL`, `WHATSUP_BOT_TOKEN`)은 `.env`·배포 환경변수로만. 코드·문서·채팅에 값 금지.
- Flyway 번호 고정: BE-0 V18, BE-2 V19, BE-3 V20, BE-4 V21, BE-5 V22, BE-7 V23, BE-8 V24. (아래 태스크 본문의 옛 번호 V18~V23은 이 표로 대체)
- 병렬 레인: 통합 브랜치 `feature/KAN-399-dining-hackathon`(BE·FE 각각)에서 BE-0 후 분기. 레인 A = BE-1→2→3→4→5→6, B = BE-7, C = BE-8, D = FE-1→2→3, E = FE-4→5→6. 레인은 서로의 코드가 없다고 가정하고 BE-0과 이 계획의 인터페이스만 의존한다.
- `src/test/.../global/storage/service/SupabaseStorageServiceTest.java`의 변경·삭제는 보안 도구 오탐 처리 결과라 **절대 커밋에 포함하지 않는다**.
- 게이트: BE `./gradlew test`(JDK 17), FE `npm run lint && npm run build`.

## Review Focus

- 같은 알림이 MacroDroid 재시도로 두 번 들어옴 → 한 번만 처리, 두 번째 응답도 200. (BE-4 테스트 `webhook_duplicateText_processedOnce`)
- 보증금 대상 회원이 멤버십으로 신청 → 보증금 입금 전에는 매칭 후보가 되면 안 됨. (BE-3 `apply_depositRequiredWithMembership_staysPaymentPending`)
- 1회권 신규 + 보증금 합산 입금(59,900)이 9,900 후보와 50,000 후보로 따로 잡혀 2건 매칭되면 안 됨 → 합산 후보 1건만. (BE-4 `reconcile_bundledPassAndDeposit_singleCandidate`)
- 멤버십 이용권 취소 복원 시 `remainingCount`가 늘어나면 안 됨. (BE-2 `refundOneTicket_periodPass_remainingUnchanged`)
- 그록봇 웹훅 페이로드에 다른 회원 데이터가 섞이면 안 됨. (BE-7 `payload_customerA_containsNoCustomerBData`)

---

### Task BE-0 (KAN-399): 병렬 레인 공통 기반

**Files:** Create `V18__hackathon_foundation.sql`, `domain/payment/dto/PendingPayment.java`, `domain/payment/enums/PaymentTargetType.java`, `domain/payment/entity/RefundAccount.java`; Modify `User.java`, `ExceptionCaseType.java`, 내 정보 응답 DTO(`/api/users/me` 응답); Test `UserTest`(엔티티 테스트 위치 관례 따름)

**Interfaces — Produces (모든 레인이 의존):**
- `users.is_demo BOOLEAN NOT NULL DEFAULT false`, `users.deposit_required BOOLEAN NOT NULL DEFAULT false`. `User.isDemo()`, `User.isDepositRequired()`, `User.markDepositRequired()`, `User.markDemo()`.
- `record PendingPayment(PaymentTargetType type, UUID targetId, UUID applicationId, String payerName, int amount)`, `enum PaymentTargetType {TICKET_PASS, DEPOSIT, REGULAR_APPLICATION}`.
- `@Embeddable RefundAccount(String bank, String accountNumber, String holder)` — 컬럼명은 사용하는 엔티티가 `@AttributeOverrides`로 정한다. `isComplete()`(세 값 모두 비어 있지 않음).
- `ExceptionCaseType.CS`(주석: "AI 상담이 사람에게 넘긴 문의").
- 내 정보 응답에 `isDemo`, `depositRequired` 필드.

- [ ] **Step 1:** `markDepositRequired_setsFlag`, `refundAccount_isComplete_falseWhenBlank` 실패 확인.
- [ ] **Step 2:** 구현 → `./gradlew test` 전체 PASS.
- [ ] **Step 3:** 커밋 `feat: 해커톤 공통 기반 — 데모·보증금 대상 플래그, 결제 대기·환불 계좌 타입 (KAN-399)`

---

## File Structure

| 영역 | 파일 |
|---|---|
| 결제 완료 공통 | `domain/ticket/service/TicketService.java`(settlePass 이동), `domain/ticket/admin/service/AdminTicketService.java`(위임), `domain/application/client/service/ApplicationService.java`(자동 승인·일반 모임 시스템 입금 확인) |
| 이용권 | `domain/ticket/entity/{TicketProductOption,TicketPass}.java`, `domain/ticket/enums/{TicketProductKind,TicketTransactionType}.java`, `domain/ticket/repository/TicketPassRepository.java`, `domain/ticket/service/MembershipExpiryScheduler.java` |
| 정책 | `domain/application/client/service/CancelPolicy.java`(새, 순수), `DiningApplicationService.java` |
| payment 도메인(새) | `domain/payment/entity/{Deposit,DepositNotification,PayoutRequest}.java`, `enums/{DepositStatus,DepositSource,DepositMatchResult,PayoutType,PayoutStatus,PaymentTargetType}.java`, `dto/PendingPayment.java`, `service/{DepositService,DepositTextParser,DepositReconciliationService,PayoutService}.java`, `controller/{DepositWebhookController,DemoDepositController}.java`, `admin/controller/AdminPaymentController.java` |
| AI CS(새, 그록봇) | `domain/bot/**`, `global/config/BotSecurityConfig.java`, `resources/cs/faq.md` |
| 데모 | `domain/auth/service/DemoAccountService.java`, `User.isDemo`, `Gathering.hidden` |
| 마이그레이션 | `resources/db/migration/V18__ticket_membership.sql` … `V23__demo_and_price_range.sql` |
| FE | `app/(main)/payments/random-table/page.tsx`, `components/payment/{DepositSimulatorCard,PaymentPendingPanel,RefundAccountForm}.tsx`, `components/mypage/DiningStatusCard.tsx`, `components/dining/CancelPreviewModal.tsx`, `app/admin/payments/page.tsx`, `components/chat/ChatMessageBubble.tsx`, `app/admin/chat/*`, `app/(auth)/login/page.tsx`, `components/admin/GatheringSessionModal.tsx`, `app/(main)/dining/history/page.tsx`, `lib/api/{ticket,payment,adminPayment,application,chat,adminChat,auth}.ts` |

---

### Task BE-1 (KAN-400): 결제 완료 처리 공통화 + 우연한 식탁 자동 승인

**Files:** Modify `TicketService.java`, `AdminTicketService.java:98-139`, `ApplicationService.java:~200-240`, `AdminApplicationService.java:198-213`; Test `src/test/.../ticket/service/TicketServiceSettleTest.java`, `.../application/service/ApplicationServiceAutoApproveTest.java`

**Interfaces — Produces:**
- `TicketService.settlePass(UUID passId, String actor): TicketPass` — `AdminTicketService.confirm` 본문(정원 검사·activate·ISSUE·결제 대기 신청 차감·확정·`ApplicationConfirmedEvent`)을 옮긴 것. `actor`는 거래 메모 접미사(`"ADMIN:{id}"`, `"WEBHOOK"`, `"SIMULATOR"`). `AdminTicketService.confirm`은 위임만.
- `ApplicationService.confirmRegularPaymentBySystem(UUID applicationId)` — `AdminApplicationService.changePayment(confirmed=true)`와 같은 효과(첫 확인 시 `ApplicationPaymentConfirmedEvent`).
- `TicketService.listPendingPasses(): List<PendingPayment>` / `ApplicationService.listPendingRegularPayments(): List<PendingPayment>` — `PendingPayment`는 BE-3에서 정의(이 태스크에서 먼저 만들어도 됨: `record PendingPayment(PaymentTargetType type, UUID targetId, UUID applicationId, String payerName, int amount)`, `PaymentTargetType {TICKET_PASS, DEPOSIT, REGULAR_APPLICATION}`, 위치 `domain/payment/dto`).
  - 이용권: `status=PENDING`, `paymentDeadline` 미경과, payerName=`user.name`, amount=`purchaseAmount`, applicationId=`pass.application?.id`.
  - 일반 모임: 일반 타입, 미삭제, `!isPaymentConfirmed`, 회차 가격>0, 상태 PENDING|CONFIRMED, payerName=`application.name`, amount=`session.getEffectivePrice()`.

- [ ] **Step 1: 실패 테스트** — `settlePass_activatesAndConfirmsPendingApplication`(PENDING 이용권+결제 대기 신청 → pass ACTIVE, 신청 CONFIRMED, USE 거래 1건, 이벤트 발행), `settlePass_alreadyActive_throws`(기존 confirm과 같은 예외), `apply_randomTable_unreviewedUser_autoApproved`(UNREVIEWED 회원 신청 → `user.isApprovedForRandomTable()==true`, 이용권 없으면 `PAYMENT_PENDING`), `apply_randomTable_restrictedUser_throws`.
- [ ] **Step 2:** `./gradlew test --tests "*TicketServiceSettleTest" --tests "*ApplicationServiceAutoApproveTest"` → FAIL.
- [ ] **Step 3:** 구현. 자동 승인은 `validateRandomTableEligibility` 통과 직후 `user.approveRandomTable()`. `listPending*` 두 메서드 추가.
- [ ] **Step 4:** 같은 명령 PASS + 기존 `AdminTicketServiceTest` PASS.
- [ ] **Step 5:** 커밋 `refactor: 입금 확인 처리를 서비스로 추출하고 우연한 식탁 신청 자동 승인 (KAN-400)`

### Task BE-2 (KAN-401): 1회권 + 기간제 멤버십

**Files:** Create `V18__ticket_membership.sql`, `TicketProductKind.java`, `MembershipExpiryScheduler.java`; Modify `TicketProductOption.java`, `TicketPass.java`, `TicketTransactionType.java`(+`EXTEND`), `TicketPassRepository.java`, `TicketService.java`, `MatchResolutionService.java:65`, `ApplicationService.java`; Test `TicketServiceMembershipTest.java`, `TicketPassTest.java`

**Interfaces — Produces:**
- `TicketProductOption.kind: TicketProductKind {COUNT, PERIOD}`, `periodMonths: Integer`.
- `TicketPass.validUntil: LocalDate`, `extendedDays: int`, `isPeriod()`, `isUsableOn(LocalDate sessionDate)`, `extend(int days)`.
- `TicketService.hasMembershipFor(UUID userId, LocalDate sessionDate): boolean`
- `TicketService.extendMembershipForFailure(UUID applicationId): void` — 신청에 연결된 USE 거래의 pass가 PERIOD면 +7일, `EXTEND` 거래(수량 0). 같은 신청에 EXTEND 있으면 무시.

마이그레이션: 컬럼 추가, 기존 상품 soft delete, 상품 4종 insert(값은 Global Constraints). `ticket_passes.valid_until DATE NULL, extended_days INT NOT NULL DEFAULT 0`.

- [ ] **Step 1: 실패 테스트**
  - `settlePass_period_setsValidUntil` — 활성화 시각 2026-10-20 → `validUntil == 2026-11-20`(1개월).
  - `tryUseOneTicket_prefersMembershipOverCount` — 둘 다 있으면 PERIOD 사용, COUNT 잔여 그대로, USE 거래 수량 0.
  - `tryUseOneTicket_membershipExpiredForSession_fallsBackOrFalse` — 회차 날짜 > validUntil이면 사용 안 함.
  - `refundOneTicket_periodPass_remainingUnchanged`.
  - `extendMembershipForFailure_once` — 두 번 호출해도 +7일 한 번, EXTEND 거래 1건.
  - `apply_membershipSameWeekTwice_throws` — 같은 ISO 주(월~일) 회차에 활성 신청이 있으면 `ErrorCode.MEMBERSHIP_WEEKLY_LIMIT`(새). 후보 회차가 서로 다른 주면 `ErrorCode.CANDIDATE_SESSIONS_DIFFERENT_WEEKS`(새, 멤버십 사용 시만).
- [ ] **Step 2:** `./gradlew test --tests "*TicketServiceMembershipTest" --tests "*TicketPassTest"` → FAIL.
- [ ] **Step 3:** 구현. `MatchResolutionService.offerResolution`에서 `ticketService.extendMembershipForFailure(application.getId())` 호출. 만료 스케줄러: 매일 00:10, `validUntil < today`인 ACTIVE PERIOD → `EXPIRE` 거래 + 상태 `USED_UP`.
- [ ] **Step 4:** PASS + 전체 `./gradlew test`.
- [ ] **Step 5:** 커밋 `feat: 기간제 멤버십 이용권과 해커톤 가격 적용 (KAN-401)`

### Task BE-3 (KAN-402): 취소 정책·보증금·노쇼

**Files:** Create `CancelPolicy.java`, `V19__deposits.sql`, `payment/entity/Deposit.java`, `payment/enums/DepositStatus.java`, `payment/service/DepositService.java`; Modify `DiningApplicationService.java:116-165`, `ApplicationService.java`(신청 시 보증금 생성), apply 요청 DTO(+`depositReturnAccount`), `User.java`(+`depositRequired`), `Attendance.java`(+`cancelLate()`), `DiningAttendanceService.java:128`(NO_SHOW 확정 훅), `TicketService.settlePass`(보증금 충족 확인), 매칭 후보 필터(`DiningMatchService` 후보 조회부); Test `CancelPolicyTest.java`, `DiningApplicationServiceCancelTest.java`, `DepositServiceTest.java`

**Interfaces — Produces:**
- `CancelPolicy.evaluate(LocalDateTime now, LocalDate sessionDate): CancelWindow {BEFORE_DEADLINE, AFTER_DEADLINE, SAME_DAY}` — 경계: `now < sessionDate-1일 00:00` → BEFORE, `now < sessionDate 00:00` → AFTER, 그 외 SAME_DAY.
- `GET /api/dining/applications/{id}/cancel-preview` → `{window, ticketRestored, refundable, depositOutcome: RETURN|FORFEIT|NONE, sanction: boolean}`.
- `Deposit(application, amount=50000, status, returnAccount: RefundAccount)`; `DepositStatus {PENDING, PAID, RETURN_PENDING, RETURNED, FORFEITED}`; `RefundAccount`은 `@Embeddable(bank, accountNumber, holder)`, `domain/payment/entity`.
- `DepositService.createPending(Application, RefundAccount)`, `markPaid(UUID applicationId)`, `isSatisfied(UUID applicationId): boolean`(보증금 없거나 PAID 이상), `requestReturn(UUID applicationId)`(→ RETURN_PENDING + BE-5 Payout), `forfeit(UUID applicationId)`, `listPendingStandalone(): List<PendingPayment>`(type DEPOSIT, amount 50000), `findUnsatisfiedApplicationIds(Collection<UUID>): Set<UUID>`.
- `User.depositRequired`, `markDepositRequired()`.

동작:
- 신청 시 `user.depositRequired`면 `depositReturnAccount` 필수(`ErrorCode.DEPOSIT_ACCOUNT_REQUIRED`), Deposit PENDING 생성, 신청은 이용권 사용 여부와 무관하게 `PAYMENT_PENDING`. 멤버십이면 USE(0) 거래는 기록하되 확정하지 않는다. 보증금 `markPaid` 후 `isSatisfied && 이용권 USE 있음`이면 신청 confirm + `ApplicationConfirmedEvent`. `settlePass`의 자동 확정도 `isSatisfied`일 때만.
- 취소: BEFORE = 기존 로직 + 보증금 `requestReturn`. AFTER = `application.cancel()`, 이용권 복원 없음, `attendance.cancelLate()`, 보증금 `requestReturn`, rebalance. SAME_DAY = AFTER와 같되 보증금 `forfeit` + `user.markDepositRequired()`. 기존 `CANCEL_WINDOW_CLOSED`는 회차 시작 이후에만.
- 노쇼 확정(`changeAttendanceStatus(→NO_SHOW)`): `markDepositRequired` + `forfeit`. 체크인(`checkIn`): `requestReturn`.
- 매칭 후보에서 `findUnsatisfiedApplicationIds` 제외.

- [ ] **Step 1: 실패 테스트**
  - `CancelPolicyTest`: 목요일 2026-10-22 회차 — `10-20 23:59 → BEFORE`, `10-21 00:01 → AFTER`, `10-22 00:00 → SAME_DAY`.
  - `cancel_afterDeadline_noRestore_depositReturned`, `cancel_sameDay_depositForfeited_userFlagged`, `cancel_beforeDeadline_ticketRestored`.
  - `apply_depositRequiredWithMembership_staysPaymentPending`, `markPaid_thenConfirmsApplication`.
  - `noShowConfirmed_marksDepositRequired`, `checkIn_requestsDepositReturn`.
  - `matchCandidates_excludeUnpaidDeposit`.
- [ ] **Step 2:** 해당 테스트 FAIL.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** 전체 `./gradlew test` PASS.
- [ ] **Step 5:** 커밋 `feat: 우연한 식탁 취소 시점 정책과 노쇼 보증금 (KAN-402)`

### Task BE-4 (KAN-403): 입금 알림 웹훅·자동 대사·시뮬레이터·입금 로그 API

**Files:** Create `V20__deposit_notifications.sql`, `payment/entity/DepositNotification.java`, `enums/{DepositSource,DepositMatchResult}.java`, `service/{DepositTextParser,DepositReconciliationService}.java`, `controller/{DepositWebhookController,DemoDepositController}.java`, `admin/controller/AdminPaymentController.java`; Modify `SecurityConfig.java`(웹훅 permitAll), `application.yml`(`payment.deposit-webhook.secret: ${DEPOSIT_WEBHOOK_SECRET:}`), `ExceptionCaseService` 사용; Test `DepositTextParserTest.java`, `DepositReconciliationServiceTest.java`, `DepositWebhookControllerTest.java`

**Interfaces — Consumes:** BE-1 `listPendingPasses`, `listPendingRegularPayments`, `settlePass`, `confirmRegularPaymentBySystem`; BE-3 `DepositService.listPendingStandalone/markPaid/findPendingByApplication`.
**Produces:**
- `DepositNotification(rawText, textHash UNIQUE(sha256), source, receivedAt, parsedName, parsedAmount, result, targetType, targetId, applicationId, exceptionCaseId, createdAt)`; `DepositSource {MACRODROID, IOS_SHORTCUT, SIMULATOR}`; `DepositMatchResult {MATCHED, IGNORED, NO_CANDIDATE, MULTIPLE_CANDIDATES, PARSE_FAILED}`.
- `DepositTextParser.parse(String raw): Optional<ParsedDeposit(name, amount)>` — 정규식 목록(푸시 1개, 문자용은 빈 자리). `[입금]`이 아니면 `Optional.empty()`이되 호출부가 IGNORED와 PARSE_FAILED를 구분할 수 있게 `isDepositAlert(raw)` 별도.
- `DepositReconciliationService.ingest(String raw, DepositSource source, LocalDateTime receivedAt): DepositNotificationResponse` — 해시 중복이면 기존 행 반환. 후보 = 이용권(+같은 신청 PENDING 보증금이면 금액 합산, 그 보증금은 단독 후보에서 제외) ∪ 단독 보증금 ∪ 일반 모임. 1건이면 정산(이용권: 보증금 `markPaid` 먼저 → `settlePass`), 0/2+건이면 `ExceptionCaseService.open(PAYMENT, …, "입금 대사 실패: …")`. 한 트랜잭션.
- `POST /api/payments/deposit-webhook` 헤더 `X-Deposit-Secret`, 바디 `{text, source?, receivedAt?}`. 시크릿 미설정·불일치 401. 바디 2KB 초과 413.
- `POST /api/payments/demo-deposit {applicationId? , passId?}` — 요청자가 `isDemo`가 아니면 403. 대상 결제 대기 건의 payerName·금액으로 샘플 형식 원문 생성(`[입금] {name}        {amount:,}원 1002-000-000***계좌 잔액 {random:,}원 {MM/dd HH:mm:ss}`) → `ingest(…, SIMULATOR, now)` → 응답에 단계 결과(`parsedName, parsedAmount, linkedApplicationId, result`).
- `GET /api/admin/payments/deposit-notifications?result=&page=` 최신순.
- `POST /api/admin/payments/deposit-notifications/{id}/link {targetType, targetId}` — 예외 건 수동 연결(같은 정산 경로) 후 예외 RESOLVED.

- [ ] **Step 1: 실패 테스트**
  - 파서: 샘플 `[입금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30` → (`한지형`, 40000). `[출금] …` → 입금 아님. 깨진 문자열 → empty.
  - `reconcile_singleMatch_settlesPass`, `reconcile_noCandidate_opensException`, `reconcile_twoSameNameSameAmount_opensException`, `reconcile_bundledPassAndDeposit_singleCandidate`(59,900 → 1건, 보증금 PAID·이용권 ACTIVE·신청 CONFIRMED), `reconcile_prefixName_matchesWhenUnique`, `reconcile_regularApplication_confirmsPayment`.
  - `webhook_wrongSecret_401`, `webhook_duplicateText_processedOnce`, `demoDeposit_nonDemoUser_403`.
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** 전체 PASS.
- [ ] **Step 5:** 커밋 `feat: 입금 알림 웹훅 자동 대사와 심사용 입금 시뮬레이터 (KAN-403)`

### Task BE-5 (KAN-404): 환불 계좌·송금 목록(환불·보증금 반환)

**Files:** Create `V21__payout_requests.sql`, `payment/entity/PayoutRequest.java`, `enums/{PayoutType,PayoutStatus}.java`, `service/PayoutService.java`; Modify `TicketService.refundPurchase`, `MatchResolutionService.chooseResolution`(REFUND 시 계좌 필수), `ResolutionChooseRequest`(+`refundAccount`), `DepositService.requestReturn`, `AdminPaymentController`; Test `PayoutServiceTest.java`, `MatchResolutionServiceTest`(갱신)

**Interfaces — Produces:**
- `PayoutRequest(user, type {REFUND, DEPOSIT_RETURN}, amount, account: RefundAccount, status {REQUESTED, SENT}, applicationId, ticketPassId, depositId, sentAt, sentBy)`.
- `PayoutService.request(PayoutType, UUID userId, int amount, RefundAccount, UUID applicationId, UUID passId, UUID depositId): PayoutRequest` — 데모 회원이면 즉시 SENT 처리 후 `PayoutSentEvent`.
- `PayoutService.markSent(List<UUID> ids, UUID adminId)` → 각 건 SENT + `PayoutSentEvent(payoutId)`; 리스너가 REFUND면 해당 USE 거래 `REFUNDED`, DEPOSIT_RETURN이면 보증금 `RETURNED`.
- `refundPurchase(Application, RefundAccount)`: `REFUND_REQUESTED → REFUND_PROCESSING`에서 멈추고 Payout 생성(금액 = 그 이용권 `purchaseAmount`).
- `GET /api/admin/payments/payouts?type=&status=`, `POST /api/admin/payments/payouts/sent {ids}`.
- 계좌는 SENT 30일 후 마스킹(`StorageCleanupScheduler` 옆 일일 작업 또는 같은 스케줄러에 추가).
- 멤버십 환불: `TicketService.refundMembership(UUID passId, UUID userId, RefundAccount account): void` + `POST /api/tickets/{passId}/refund {refundAccount}`. 첫 참석 판정 `DiningAttendanceService.hasAttendedSince(UUID userId, LocalDateTime since): boolean`(새). 확정 전 진행 신청은 함께 취소, 확정 테이블 신청이 있으면 `ErrorCode.MEMBERSHIP_REFUND_ACTIVE_TABLE`. 성공 시 pass `CANCELLED` + Payout(REFUND, `purchaseAmount`). 빈 순환 의존이 생기면 이벤트로 분리.

- [ ] **Step 1: 실패 테스트** — `refund_stopsAtProcessing_createsPayout`, `markSent_refund_marksRefunded`, `markSent_depositReturn_marksReturned`, `request_demoUser_autoSent`, `chooseRefund_withoutAccount_throws`, `refundMembership_afterAttendance_throws`, `refundMembership_beforeAttendance_createsPayoutAndCancelsPass`.
- [ ] **Step 2-4:** FAIL → 구현 → 전체 PASS.
- [ ] **Step 5:** 커밋 `feat: 환불·보증금 반환 송금 요청과 운영자 송금 목록 (KAN-404)`

### Task BE-6 (KAN-405): 확정 후 인원 부족 시 남은 멤버 선택지

**Files:** Modify `DiningTableService.rebalance`(line 71~), `MatchResolutionService.offerResolution`(마감 후 환불 허용 플래그); Test `DiningTableServiceTest`(추가)

**Interfaces — Produces:** `MatchResolutionService.offerResolution(Application, Collection<UUID> wishedSessionIds, boolean refundAllowedAfterDeadline)` — 기존 2-인자 메서드는 `false`로 위임. 테이블 해체 사유 `"인원 부족(확정 후 취소)"`.

동작: 충원 → 같은 회차 `< max` 테이블 합류가 모두 실패하면 테이블 `DISSOLVED`, 단체방 시스템 공지("인원이 부족해 이번 테이블은 진행되지 않아요. 앱에서 다음 일정이나 환불을 선택해 주세요"), 남은 멤버 각자 `offerResolution(…, true)` + 보증금 `requestReturn`, `ExceptionCase(CONFLICT)` 생성 안 함.

- [ ] **Step 1:** `rebalance_threeLeftNoFill_dissolvesAndOffersResolutions`(멤버 3 → resolution 3건, 테이블 DISSOLVED, CONFLICT 예외 0건), `rebalance_threeLeft_refillFromWaiting`(기존 동작 유지).
- [ ] **Step 2-4:** FAIL → 구현 → PASS.
- [ ] **Step 5:** 커밋 `feat: 확정 테이블 인원 부족 시 남은 참가자에게 해결 선택지 제공 (KAN-405)`

### Task BE-7 (KAN-406): 1:1 문의방 그록봇 AI 자동응답 (웹훅 발송 + 답변 수신 API)

> **2026-10-20 변경(BE-7b):** 외부 에이전트 웹훅 URL을 쓸 수 없어 웹훅 푸시를 폐기하고 "Slack 신호(`SLACK_SIGNAL_WEBHOOK_URL`, 고정 문구) + `GET /api/bot/pending` lease(5분, `FOR UPDATE SKIP LOCKED`, 응답 `{"items":[...]}`) + `POST /api/bot/replies`(LEASED일 때만)" 방식으로 전환했다(V25). 아래 본문의 웹훅 발송·`GROK_WEBHOOK_*`·SENT/3분 타임아웃 내용은 BE-7b로 대체되었다. 나머지(인증·멱등·이관·일시정지·한도·대상 고객)는 유지.

AI는 외부 에이전트(Grok Bot). 우리 서버가 웹훅으로 문의를 보내고, 에이전트가 우리 답변 API를 호출해 답을 등록한다. 봇은 **읽기 전용**: 데이터 변경(취소·환불·입금 확인 등) 금지, 할 수 있는 건 "답변 등록"과 "담당자 이관 표시"뿐. 비밀값(`GROK_WEBHOOK_URL`, `GROK_WEBHOOK_KEY`, `WHATSUP_BOT_TOKEN`)은 환경변수만, 로그 출력 금지.

**Files:** Create `V23__bot_dispatch.sql`, `domain/bot/entity/{BotDispatch,BotSetting}.java`, `domain/bot/enums/BotDispatchStatus.java`, `domain/bot/service/{BotDispatchListener,BotDispatchService,BotPayloadBuilder,BotWebhookClient,BotReplyService,BotDispatchScheduler}.java`, `domain/bot/controller/BotReplyController.java`, `domain/bot/admin/controller/AdminBotController.java`, `global/config/BotSecurityConfig.java`(별도 필터 체인), `resources/cs/faq.md`; Modify `ChatRoom.java`(+`needsHuman`, `botPaused`), `ChatMessage.java`(+`aiGenerated`), `ChatService.java`(+`postBotMessage`, 관리자가 INQUIRY 방에 답하면 `botPaused=true`), `AdminChatService.java`(+봇 재개), 채팅 응답 DTO(`aiGenerated`, 방 요약·상세에 `needsHuman`, `botPaused`, 관리자용 `handoffReason`), `application.yml`/`application-test.yml`(`bot.*`), `.github/workflows/deploy.yml`(세 시크릿 전달 + `BOT_ENABLED`, `BOT_ALLOWED_USER_IDS`); Test `domain/bot/**`

**설정 (`bot.*`):** `enabled ${BOT_ENABLED:false}`(마스터 스위치), `allowed-user-ids ${BOT_ALLOWED_USER_IDS:}`(쉼표 UUID), `webhook-url ${GROK_WEBHOOK_URL:}`, `webhook-key ${GROK_WEBHOOK_KEY:}`, `reply-token ${WHATSUP_BOT_TOKEN:}`, `reply-url https://api.whatsup.house/api/bot/replies`(시작 시 `https://` + 호스트가 `whatsup.house` 또는 `*.whatsup.house`인지 검증, 아니면 봇 비활성 + 경고 로그), `debounce-seconds 10`, `reply-timeout-minutes 3`, `per-user-hourly-limit 20`, `daily-limit 300`, `max-text-length 2000`. 테스트 프로파일은 `enabled=false` 기본, 테스트에서 필요 시 켬.

**대상 고객:** `bot.enabled` && 관리자 화면 전역 토글(`bot_settings.enabled`) ON && (고객 userId ∈ `allowed-user-ids` **또는 `user.isDemo()`**) && 방 `botPaused=false`. 그 외 고객은 지금처럼 사람만 응답.

**DB (V23):**
- `bot_dispatch(id UUID PK, event_id UUID UNIQUE, conversation_id UUID, message_id UUID UNIQUE, customer_id UUID, status VARCHAR(20) — PENDING|SENT|REPLIED|HANDED_OFF|FAILED, attempts INT, last_error TEXT, send_after TIMESTAMP, next_attempt_at TIMESTAMP, sent_at TIMESTAMP, handoff_reason TEXT, created_at, updated_at)`. `last_error`에 비밀값·요청 본문 금지(상태 코드·예외 클래스만).
- `bot_settings(id SMALLINT PK=1, enabled BOOLEAN DEFAULT true, updated_by UUID, updated_at)` 1행 시드.
- `chat_rooms.needs_human BOOLEAN DEFAULT false`, `chat_rooms.bot_paused BOOLEAN DEFAULT false`, `chat_messages.ai_generated BOOLEAN DEFAULT false`.
- 봇 전용 시스템 계정 1행 insert(고정 UUID 상수 `BotAccount.ID`, email `bot@whatsup.house`, 닉네임 `와썹 AI`, 로그인 불가 랜덤 비밀번호 해시, 관리자 아님). 봇이 INQUIRY 방에 글을 쓸 수 있게 ChatAccessPolicy/멤버십을 최소 변경.

**1. 발송 (고객 메시지 → 웹훅)**
- `BotDispatchListener`: `ChatMessageCreatedEvent` `@TransactionalEventListener(AFTER_COMMIT) @Async`. INQUIRY 방이고 발신자 = 방의 `inquiryUserId`이고 대상 고객일 때만.
- 디바운스: 같은 방에 PENDING 행이 있으면 그 행의 `message_id`를 새 메시지로, `send_after = now + 10s`로 갱신(한 건으로 묶음). 없으면 새 PENDING 행(event_id 새 UUID). 같은 메시지 이벤트 중복은 `message_id UNIQUE`로 1회만.
- 같은 방에 SENT 상태 dispatch가 있는데 고객이 새 메시지를 보내면 새 PENDING을 만든다(이전 SENT의 답변은 2절 규칙으로 409 처리됨).
- 한도: 고객별 최근 1시간 dispatch 수 ≥ 20 또는 오늘 전체 ≥ 300이면 발송하지 않고 담당자 이관(3절 이관 처리).
- `BotDispatchScheduler`(2초 주기): `PENDING && send_after <= now` 또는 재시도 대기 중 `next_attempt_at <= now`인 행을 행 잠금(`FOR UPDATE SKIP LOCKED`)으로 집어 발송.
- `BotPayloadBuilder.build(dispatch)`: **dispatch.customerId 하나로만** 기존 서비스 호출(내 신청 목록·상세 = `DiningApplicationService`/`ApplicationService`의 본인 조회, 이용권 = `TicketService.getMyTickets(userId)`, `user.isDepositRequired()`). 다른 고객 조회 메서드 호출 금지. 전화번호는 뒤 4자리만, 계좌번호는 앞 3자리+`***`, 이메일 제외. history = 그 방 최근 10개(role: 고객=customer, 관리자=agent, aiGenerated=bot, 시스템 메시지 제외), faq = `faq.md`를 `{q, a}` 목록으로 파싱.
- 본문: `{eventId, conversationId, messageId, customer{id, displayName(닉네임)}, message{text, createdAt ISO-8601}, history[], context{applications[], tickets{}}, faq[], replyUrl}`.
- `BotWebhookClient`: `POST {webhook-url}`, `Authorization: Bearer {webhook-key}`, JSON, 연결 5초·응답 10초 타임아웃. 200 → `SENT`, `sent_at=now`. 400·429·5xx·네트워크 오류 → `attempts++`, 백오프 5s → 30s → 2m(`next_attempt_at`), 3회 재시도 후 실패면 `FAILED` + 이관.
- 무응답 감시: `SENT && sent_at + 3분 <= now` → `HANDED_OFF`(reason "응답 시간 초과") + 이관.

**2. 답변 수신 `POST /api/bot/replies`**
- `BotSecurityConfig`: `securityMatcher("/api/bot/**")` 전용 `SecurityFilterChain`(@Order 우선), stateless, CSRF off, JWT 필터 없음, `BotTokenFilter`가 `Authorization: Bearer` 값을 `MessageDigest.isEqual`로 비교. 불일치·누락 401. 일반 JWT/세션으로는 통과 불가. `reply-token` 미설정이면 항상 401.
- 본문 `{eventId, conversationId, inReplyToMessageId, action: "reply"|"handoff", text, handoffReason}`, 헤더 `Idempotency-Key`(= eventId, 다르면 422).
- 검증 순서: dispatch 없음 → 404, conversationId·inReplyToMessageId 불일치 → 403, 이미 REPLIED/HANDED_OFF → 409(아무것도 안 함), 같은 방에 이보다 최신 dispatch 존재 → 409(오래된 답변 폐기, 상태 그대로), 상태가 SENT 아님 → 409, text 비었거나 2000자 초과 → 422. 처리는 dispatch 행 잠금 후 한 트랜잭션(동시 중복 호출에도 메시지 1개).
- text는 `HtmlUtils.htmlEscape` 후 저장.
- `reply`: `ChatService.postBotMessage(roomId, text)` — 발신자 봇 계정, `aiGenerated=true`, `ChatMessageCreatedEvent` 발행(기존 WebSocket 브로드캐스트·푸시 그대로; 봇 리스너는 봇 발신 메시지 무시). dispatch `REPLIED`.
- `handoff`: 같은 방식으로 text 게시 + 이관 처리 + `handoff_reason` 저장(관리자만 조회). dispatch `HANDED_OFF`.

**3. 이관 처리 (공통 `BotDispatchService.handOff(dispatch, reason)`)**
- 방 `needsHuman=true`, `ExceptionCaseService.open(CS, null, null, null, "문의방 {roomId}: {reason}")`. 웹훅 실패·타임아웃·한도 초과 이관이면 고객에게 고정 문구 게시: "담당자에게 연결했어요. 곧 답변드릴게요." (봇 계정, aiGenerated=true). 같은 방에 열린 CS 예외가 있으면 새로 만들지 않음.
- 담당자(관리자)가 INQUIRY 방에 메시지를 보내면 방 `botPaused=true`(이후 고객 메시지는 발송 안 함).
- `POST /api/admin/chat/rooms/{id}/bot/resume` → `botPaused=false`, `needsHuman=false`, 그 방 열린 CS 예외 RESOLVED.
- `GET/PUT /api/admin/bot/settings {enabled}` — 전역 토글.

**4. 테스트 (합성 고객 A·B, 가상 데이터만; 웹훅은 `MockRestServiceServer`)**
고객 A: 신청 1건 입금확인 완료, 이용권 잔여 3회. 고객 B: 신청 1건 입금 대기, 이용권 없음.
1. `payload_customerA_containsNoCustomerBData`(A·B 동시 발송 포함, B의 id·이름·신청 id가 A 페이로드 직렬화 문자열에 없음)
2. `duplicateMessageEvent_dispatchedOnce`
3. `concurrentInquiries_eachReplyLandsInOwnRoom`
4. `webhook400or5xx_retriesThenFailed_handsOff`(시도 4회 = 최초+3재시도, 상태 FAILED, needsHuman)
5. `reply_sameEventIdTwice_secondIs409_singleMessage`
6. `reply_wrongToken_401`, `reply_otherRoomConversationId_403`
7. `sentWithoutReply3Minutes_handsOff`
8. `refundRequest_handoffReply_marksNeedsHuman`
추가: `debounce_threeMessagesWithin10s_singleDispatchWithLastMessageId`, `adminMessage_pausesBot`, `nonAllowlistedCustomer_noDispatch`, `demoCustomer_dispatched`, `hourlyLimitExceeded_handsOff`, `replyUrl_nonWhatsupHost_disablesBot`, `reply_staleDispatch_409`.

- [ ] 실패 테스트 작성 → FAIL 확인 → 구현 → `./gradlew test` 전체 PASS.
- [ ] 커밋 `feat: 문의방 그록봇 AI 자동응답 웹훅 발송과 답변 수신 API (KAN-406)`
- [ ] 리포트에 변경 파일 목록, 환경변수 목록, 마이그레이션 SQL 요약, 스테이징 curl 수동 테스트 방법(웹훅 수신 확인용 요청 예시, `/api/bot/replies` reply·handoff·중복·잘못된 토큰 예시 — 토큰 값 대신 `$WHATSUP_BOT_TOKEN` 변수 사용)을 적는다.

### Task BE-8 (KAN-407): 데모 계정·숨김 종류·가격대·추천 회차

**Files:** Create `V23__demo_and_price_range.sql`(`users.is_demo`, `gatherings.hidden`, 표준 폼 `PRICE_RANGE` 질문: multi, 선택지 `10,000~20,000원`·`20,000~30,000원`·`30,000원 이상 (상관 없음)`), `DemoAccountService.java`; Modify `AuthController`(+`POST /api/auth/demo`), `SecurityConfig`(permitAll), `ReservedQuestionKey`(+`PRICE_RANGE`), 모임 목록·홈·추천·`DINING_NEXT_SESSIONS` 조회(숨김 필터), `DiningConfirmService.assignVenue`(가격대 우선), 어드민 사용자·대시보드 집계(데모 제외), `DiningHistory` 컨트롤러(+추천); Test `DemoAccountServiceTest.java`, `GatheringServiceHiddenTest.java`, `DiningConfirmServiceVenueTest`(추가)

**Interfaces — Produces:**
- `POST /api/auth/demo` → 새 데모 회원(name `체험{4자리}`, email `demo+{uuid8}@whatsup.house`, 랜덤 비밀번호, 프로필 샘플 채움, `isDemo=true`, `randomTableEligibility=APPROVED`) 생성 후 `AuthCookieProvider`로 로그인 쿠키. IP당 분당 3회(Redis INCR, 초과 429).
- 모임 목록 규칙: 데모 회원 → `hidden=true`만, 그 외(비회원 포함) → `hidden=false`만, 관리자 → 전체.
- `GET /api/dining/me/recommendations` → 최대 3개 OPEN·마감 전·비숨김(데모는 숨김) 회차, 지난 참석 지역 일치 우선 → 같은 요일 → 날짜 순.
- 식당 배정: 테이블 멤버 `PRICE_RANGE` 답의 교집합에 `Venue.priceRange`가 들면 우선, 없으면 기존 순서.

- [ ] **Step 1:** `createDemo_setsDemoAndApproved`, `createDemo_rateLimited_429`, `list_demoUser_onlyHidden`, `list_anonymous_excludesHidden`, `assignVenue_prefersPriceRangeIntersection`, `recommendations_prefersPastRegion`.
- [ ] **Step 2-4:** FAIL → 구현 → 전체 PASS.
- [ ] **Step 5:** 커밋 `feat: 심사용 체험 계정·숨김 모임, 식사 가격대와 추천 회차 (KAN-407)`

### Task FE-1 (KAN-408): 이용권 선택·결제 대기·보증금 반환 계좌

**Files:** Modify `app/(main)/payments/random-table/page.tsx`, `components/gathering/DiningApplyFlow.tsx`, `lib/api/ticket.ts`, `lib/api/application.ts`, `messages/*.json`(ko 우선, 나머지는 ko 복사); Create `components/payment/PaymentPendingPanel.tsx`, `components/payment/RefundAccountForm.tsx`
**Consumes:** BE-2 상품 `kind/periodMonths`, 내 이용권 `validUntil`; BE-3 `depositRequired`(내 정보), 신청 요청 `depositReturnAccount`; BE-1 결제 대기 금액.

- [ ] 상품 카드 4종(1회권/멤버십 1·3·6개월, 월 환산가 표기), 보유 멤버십 있으면 "멤버십으로 신청" 기본 선택과 남은 기한.
- [ ] 결제 대기 패널: 입금 계좌·금액(보증금 포함 시 내역 분리 표기)·**입금자명 = 실명** 강조 박스·기한·"입금되면 자동으로 확인돼요" 안내.
- [ ] 보증금 대상이면 신청 단계에 `RefundAccountForm`(은행·계좌번호·예금주) 필수 + 사유 안내("이전 노쇼 이력으로 보증금 50,000원이 필요해요. 참석 체크인 후 돌려드려요").
- [ ] 화요일 24:00 마감·당일 취소·노쇼 정책 요약 고정 노출(PAY-02).
- [ ] `npm run lint && npm run build` PASS, 모바일 375px 확인. 커밋 `feat: 멤버십 이용권 선택과 입금 대기·보증금 계좌 입력 화면 (KAN-408)`

### Task FE-2 (KAN-409): 심사용 입금 시뮬레이터 카드

**Files:** Create `components/payment/DepositSimulatorCard.tsx`, `lib/api/payment.ts`; Modify `PaymentPendingPanel.tsx`
**Consumes:** BE-4 `POST /api/payments/demo-deposit` 응답 `{rawText, parsedName, parsedAmount, linkedApplicationId, result}`; 내 정보 `isDemo`.

- [ ] 데모 회원일 때만 렌더. 제목 "심사용 입금 시뮬레이터", 설명 "실제로는 운영자 폰에 은행 알림이 오고, 와썹이 자동으로 확인합니다".
- [ ] 버튼 "{금액}원 입금하기" → 카드 안 "운영자 폰" 프레임에 응답 `rawText`가 푸시처럼 슬라이드 인 → ①알림 수신 ②이름·금액 확인 ③신청 연결 ④결제 완료 를 400ms 간격으로 체크(결과가 MATCHED가 아니면 해당 단계에서 빨간 표시 + "예외함으로 넘어갔어요") → 결제 상태 쿼리 invalidate로 상태 카드 갱신.
- [ ] `prefers-reduced-motion`이면 애니메이션 없이 즉시 표시.
- [ ] 게이트 PASS. 커밋 `feat: 심사용 입금 시뮬레이터 카드 (KAN-409)`

### Task FE-3 (KAN-410): 취소 미리보기·환불 계좌·멤버십 환불

**Files:** Create `components/dining/CancelPreviewModal.tsx`; Modify `components/mypage/DiningStatusCard.tsx`, 해결 선택 UI(REFUND 선택 시 `RefundAccountForm`), 내 이용권 화면(멤버십 환불 버튼), `lib/api/application.ts`, `lib/api/ticket.ts`
**Consumes:** BE-3 `cancel-preview`; BE-5 `refundAccount` 요청 필드와 멤버십 환불 엔드포인트(`POST /api/tickets/{passId}/refund {refundAccount}`).

- [ ] 취소 버튼 → 미리보기 모달(이용권 복원/환불 불가/보증금 반환·몰수/이용 제한 문구를 window별로) → 확인 시 취소.
- [ ] 환불 선택·멤버십 환불 시 계좌 입력 후 상태 "송금 대기" 표시(REFUND_PROCESSING), 완료 시 "환불 완료".
- [ ] 상태 카드에 보증금 상태 배지(입금 대기/보관 중/반환 대기/반환 완료/몰수).
- [ ] 게이트 PASS. 커밋 `feat: 취소 결과 미리보기와 환불 계좌 입력 (KAN-410)`

### Task FE-4 (KAN-411): 운영자 입금 알림 로그·송금 목록·예외 연결

**Files:** Create `app/admin/payments/page.tsx`(탭: 입금 알림 로그 / 송금 목록), `lib/api/adminPayment.ts`; Modify 어드민 사이드바, `app/admin/dining/exceptions`(PAYMENT 건 "신청에 연결", CS 건 "문의방 열기")
**Consumes:** BE-4 로그·link API, BE-5 payouts API.

- [ ] 로그 표: 시각·출처·원문·파싱 이름/금액·결과 배지·연결 신청 링크, 결과 필터, 10초 폴링.
- [ ] 송금 목록: 유형 탭(환불/보증금 반환), 은행·계좌·예금주·금액, 체크박스 일괄 "송금 완료".
- [ ] 게이트 PASS. 커밋 `feat: 운영자 입금 알림 로그와 송금 목록 (KAN-411)`

### Task FE-5 (KAN-412): AI 자동응답 라벨 + 운영자 담당자 필요 배지·봇 재개·전역 토글

**Files:** Modify `components/chat/ChatMessageBubble.tsx`, `lib/api/chat.ts`, 어드민 채팅 방 목록·방 헤더, `lib/api/adminChat.ts`; 어드민 봇 설정 위치는 채팅 관리 화면 상단 토글
**Consumes:** BE-7 메시지 `aiGenerated`, 방 요약·상세 `needsHuman`, `botPaused`, 관리자 방 상세 `handoffReason`, `POST /api/admin/chat/rooms/{id}/bot/resume`, `GET/PUT /api/admin/bot/settings {enabled}`.

- [ ] 고객 말풍선: `aiGenerated` 메시지에 "AI 자동응답" 라벨(발신자명 옆), 봇 메시지는 고객 쪽에서도 "와썹하우스" 계열로 보이게.
- [ ] 어드민 방 목록: `needsHuman` 방에 "담당자 필요" 배지, 목록 상단 정렬 우선.
- [ ] 어드민 방 헤더: 봇 상태(동작 중/일시정지), `handoffReason` 표시, "봇 재개" 버튼(일시정지 또는 담당자 필요일 때).
- [ ] 어드민 채팅 화면 상단 "AI 자동응답 전체 ON/OFF" 토글(확인 대화상자).
- [ ] 게이트 PASS. 커밋 `feat: 문의방 AI 자동응답 표시와 운영자 봇 제어 (KAN-412)`

### Task FE-6 (KAN-413): 체험 시작·가격대·추천 회차·회차 기본값

**Files:** Modify `app/(auth)/login/page.tsx`, `lib/api/auth.ts`, `components/admin/GatheringSessionModal.tsx`, `app/(main)/dining/history/page.tsx`; 신청 폼은 `PRICE_RANGE`가 reserved 질문으로 자동 렌더되는지 확인만
**Consumes:** BE-8 `POST /api/auth/demo`, `GET /api/dining/me/recommendations`.

- [ ] 로그인 화면 "심사용 체험 시작" 버튼 → 성공 시 우연한 식탁 숨김 종류 페이지로 이동.
- [ ] 회차 모달: 지역 선택지 4개 고정(홍대/합정·잠실·강남/신논현·을지로/종로), 날짜 입력 시 마감·매칭 실행 기본값 = 날짜-1일 00:00.
- [ ] 이력 화면 상단 "다음 추천 회차" 카드(최대 3개, 신청 링크).
- [ ] 게이트 PASS. 커밋 `feat: 심사용 체험 로그인과 추천 회차, 회차 마감 기본값 (KAN-413)`

### Task OPS-1 (KAN-414): 데모 데이터·실기기 연결·리허설·제출

**Files:** Create `backend/scripts/seed_dining_demo.py`(또는 빌더 선택 언어), `backend/docs/deposit-webhook-setup.md`(MacroDroid·단축어 설정 방법, 시크릿 값은 적지 않음)

- [ ] 숨김 RANDOM_TABLE 종류 1개 + 데모 회차 2개(가까운 시각), 식당 풀, 가상데이터에서 회차당 12~20명 데모 회원·신청·결제 완료 시드. 동명이인 PAYMENT 예외 1건 시드.
- [ ] 운영 `.env`에 `DEPOSIT_WEBHOOK_SECRET`, `SLACK_SIGNAL_WEBHOOK_URL`, `WHATSUP_BOT_TOKEN`, `BOT_ENABLED`, `BOT_ALLOWED_USER_IDS` 설정, 배포.
- [ ] MacroDroid(안드로이드) 실기기: 실제 소액 이체 → 결제 완료 확인, 30초 영상 녹화.
- [ ] 심사 전용 관리자 계정 생성(제출 후 비활성화 메모).
- [ ] PRD 18장 12단계를 실서비스에서 데모 계정으로 1회 완주, 막히는 단계 기록·수정.
- [ ] 10/21 20:00 배포 동결, 제출.

---

## 실행 순서와 병렬

BE-1 → BE-2 → BE-3 → BE-4 → BE-5 순서 의존(같은 서비스 파일을 연달아 만짐). BE-6·BE-7·BE-8은 BE-1 이후 서로 독립(각자 다른 파일; BE-6은 BE-3·BE-5의 `requestReturn`·계좌 필드를 쓰므로 BE-5 뒤가 안전). FE-1~3은 해당 BE API 계약만 있으면 목으로 병행 가능, FE-4는 BE-4·5 뒤, FE-5는 BE-7 뒤, FE-6은 BE-8 뒤. 시간이 모자라면 스펙 11장의 자르는 순서를 따른다.
