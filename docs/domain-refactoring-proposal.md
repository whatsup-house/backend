# 도메인 구조 개선 제안서

> 작성일: 2026-07-03
> 상태: 의사결정 완료, 실행 대기
> 관련 문서: `sonarqube-analysis-report.md`, `review.md`

---

## 1. 배경

SonarQube 정적 분석과 코드 리뷰 결과, 기능 단위(KAN 티켓)로 코드가 누적되면서 도메인 구조에 부채가 쌓인 것이 확인됐다. 핵심 문제는 네 가지다.

| # | 문제 | 심각도 |
|---|------|--------|
| 1 | Participant 계층이 반쪽 추상화로 남아 3중 데이터 중복(User/Participant/Application) 유발 | 높음 |
| 2 | Ticket 도메인에 레거시·신규 경로가 공존 (상품 모델 2벌, 환불 경로 2벌) | 높음 |
| 3 | 이용권 잔액 변경이 거래원장(TicketTransaction) 없이 가능한 경로 존재 | 중간 (현재 죽은 코드) |
| 4 | 크로스 도메인 리포지토리 직접 주입 28건 — 도메인 경계 붕괴 | 높음 |

## 2. 확정된 의사결정

- **D1. 우연한 식탁(RANDOM_TABLE)은 회원 전용으로 전환한다.** 비회원 이용권 구매를 막으면 사실상 비회원 참가가 불가능해지므로, 신청 단계부터 회원 전용으로 정리한다. 일반 게더링의 비회원 신청은 유지한다.
- **D2. 레거시 코드는 삭제한다.** 하위 호환 유지보다 중복 제거를 우선한다.
- **D3. 잔액-원장 불변식을 도입한다.** 이용권 잔여 수를 바꾸는 모든 코드는 반드시 거래내역을 남긴다.
- **D4. 도메인 경계를 정리한다.** 타 도메인 접근은 리포지토리 직접 주입이 아닌 서비스/이벤트를 통한다.

---

## 3. D1 — Participant 제거

### 3.1 왜 제거 가능한가

Participant는 "비회원도 이용권을 소유할 수 있다"는 요구 때문에 존재했다(회원/비회원 통합 소유 주체, KAN-276). 우연한 식탁이 회원 전용이 되면 이용권 소유자는 항상 User이므로 이 계층의 존재 이유가 사라진다.

### 3.2 변경 범위

**엔티티**

- `Participant` 삭제. `Application.participant` → `Application.user` (nullable, 비회원 신청은 NULL + 기존 name/phone/email 필드 사용 — 이미 존재함)
- `TicketPass.participant` → `TicketPass.user` (NOT NULL)
- `Participant.randomTableEligibility`, `accountStatus` → `User`로 이동 (회원 전용 정책이 되므로 자연스러움)
- `Participant.emailVerifiedAt` → 삭제. 비회원 이메일 인증은 Redis 기반 인증 플로우로 이미 커버됨

**API / 서비스**

- 삭제: `GET /api/tickets/guest`, `POST /api/tickets/guest/purchase`, `TicketService.purchaseGuest()`, `getGuestTickets()`, `GuestOverviewService` 및 컨트롤러 전체
- 삭제: `ParticipantService`, `ParticipantRepository`, participant enums
- 수정: `ApplicationService.applyInternal()` — RANDOM_TABLE + 비회원 신청 시 즉시 거부 (신규 ErrorCode: `RANDOM_TABLE_MEMBERS_ONLY`)
- 수정: `MatchingService` 등 participant 참조 14개 파일

**DB 마이그레이션**

1. `applications.user_id`, `ticket_passes.user_id` 컬럼 추가, participant 경유로 백필
2. 비회원 소유 ACTIVE 이용권 존재 여부 사전 확인 — **있다면 소진/환불 처리 전까지 배포 불가** (운영 확인 필요)
3. `users`에 `random_table_eligibility`, `account_status` 추가, participant에서 백필
4. `participants` 테이블은 한 릴리스 유지 후 drop (롤백 대비)

### 3.3 프론트 영향 범위 (2026-07-03 프론트 코드 확인 완료)

비회원 이용권 기능은 프론트에서 실사용 중이므로 **백엔드 엔드포인트 제거와 프론트 배포를 동기화**해야 한다. 제거 대상:

| 파일 | 제거 내용 |
|------|-----------|
| `app/(main)/payments/random-table/page.tsx` | `isGuestPurchase` 분기 (예약번호 기반 비회원 결제 플로우) |
| `app/(main)/guest/page.tsx`, `lib/api/guest.ts` | 비회원 개요 화면 (백엔드 `GuestOverviewService` 대응) |
| `lib/api/ticket.ts` | `fetchGuestTickets`, `purchaseGuestTicketPass` |
| `lib/hooks/useTickets.ts` | `useGuestTickets`, `usePurchaseGuestTicketPass` |
| `lib/api/types.ts` | `GuestTicketPurchaseRequest`, `GuestOverview` 타입 |
| `components/mypage/GuestApplicationCheck.tsx` | GuestOverview 참조 부분 확인 후 정리 |

추가 작업: 우연한 식탁 상세에서 비회원에게 "회원 전용" 안내 + 가입 유도 노출.
운영: 기존 비회원 이용권 보유자 CS 정책 결정 필요 (환불/이관).

### 3.4 API 계약 보존 판정

- **D3, D4**: API 영향 없음 (100% 내부 리팩토링)
- **D1 내부 모델 변경**: API 보존 가능 — `MyTicketsResponse`의 `accountStatus`/`randomTableEligibility`는 enum을 User로 옮겨도 JSON 스키마 동일
- **D1 엔드포인트 폐기**: `GET /api/tickets/guest`, `POST /api/tickets/guest/purchase` 2건만 파괴적 변경 (계획된 폐기, 프론트와 동기 배포)
- **D2 레거시 `product` enum**: 프론트 확인 결과 구매 호출이 `productId`만 전송 (`random-table/page.tsx:74,77`) → **프론트와 무관하게 즉시 삭제 가능**. 프론트 `types.ts`의 미사용 `product?` 필드는 별도 정리

---

## 4. D2 — Ticket 레거시 삭제

| 삭제 대상 | 사유 |
|-----------|------|
| `TicketProduct` enum 및 TicketPass의 `product` 필드, enum 기반 생성자 | `TicketProductOption` 엔티티로 대체 완료된 레거시. 상품 모델 단일화 |
| `TicketService.purchase()` 오버로드 3개 → 1개로 통합 | enum 경로 제거에 따른 정리 |
| `refundOneTicket(User)` | 호출부 없는 죽은 코드. "추측 기반 환불" + 원장 미기록으로 설계 결함. Application 기반 환불만 유지 |
| `useOneTicket(User)`, `tryUseOneTicket(Participant)` 단일 인자 오버로드 | 호출 경로 정리, Application 연계 버전만 유지 |
| `getProductLabel()`의 enum fallback | 상품 모델 단일화 후 불필요 |

기존 데이터: `ticket_passes.product`(enum 문자열)만 있고 `product_id`가 NULL인 행은 `product_name`/`total_count`/`purchase_amount`가 이미 스냅샷으로 저장돼 있어 enum 제거 후에도 표시에 문제없음. 컬럼은 한 릴리스 유지 후 drop.

---

## 5. D3 — 잔액-원장 불변식

**원칙: `remainingCount`를 변경하는 유일한 진입점은 TicketTransaction을 함께 저장한다.**

현재 원장 미기록 경로는 `refundOneTicket(User)`뿐이며 D2에서 삭제된다. 재발 방지를 위해:

- `TicketPass.deductOne()`/`refundOne()`/`adjustRemaining()`을 package-private으로 낮추고, 원장 기록을 포함한 `TicketService` 메서드로만 호출 가능하게 제한
- 검증 테스트 추가: 각 증감 시나리오 후 `sum(transaction.amount) == totalCount - remainingCount` 확인

---

## 6. D4 — 도메인 경계 정리

### 6.1 규칙 (CLAUDE.md에 추가 제안)

> 타 도메인의 Repository를 직접 주입하지 않는다. 타 도메인 데이터가 필요하면 해당 도메인의 Service를 주입하거나, 쓰기 부수효과는 도메인 이벤트로 처리한다.

### 6.2 우선 정리 대상 (현재 28건 중)

| 대상 | 조치 |
|------|------|
| `ApplicationAnswer` + Repository가 form 도메인에 위치 | application 도메인으로 이동 (신청 데이터의 소유권 정상화) |
| `ApplicationService` — 6개 타 도메인 repo 주입, 13개 의존성 | Query/Cancellation 분리 + 타 도메인 접근을 서비스 경유로 전환. Participant 제거로 의존성 2개 자동 감소 |
| `MatchingService` — 5개 타 도메인 repo 주입 | 필요한 조회를 각 도메인 서비스 메서드로 노출 |

### 6.3 함께 처리할 안전성 이슈

- **정원 체크 race condition**: `applyInternal()`의 좌석 확인이 락 없는 count → save 구조. Gathering 행에 `PESSIMISTIC_WRITE` 적용 (이용권 차감과 동일 패턴)
- **Redis 인증 소비 시점**: `consumeGuestEmailVerification()`이 트랜잭션 도중 실행 → 롤백 시 인증만 소비됨. AFTER_COMMIT으로 이동
- **예약 questionKey 상수화**: "name"/"phone"/"email" 매직 스트링 → enum 강제, 폼 수정 시 시스템 예약 질문 보호

---

## 7. 실행 순서

| 단계 | 내용 | 비고 |
|------|------|------|
| 1 | 비회원 ACTIVE 이용권 보유 현황 확인 + CS 정책 결정 | **배포 전 필수, 운영 확인** |
| 2 | D2 레거시 삭제 + D3 불변식 (Ticket 도메인 내부, 파급 적음) | 선행 가능 |
| 3 | D1 Participant 제거 (마이그레이션 포함) | 프론트 배포와 동기화 필요 |
| 4 | D4 도메인 경계 정리 + 안전성 이슈 3건 | 기능 변경 없는 리팩토링 |
| 5 | SonarCloud 재분석 + JaCoCo 연동 | 효과 측정 |

## 8. 리스크

- **데이터**: 비회원 이용권 잔여분 처리 미결 시 3단계 진행 불가
- **기능 회귀**: participant 참조 14개 파일 수정 — 신청/취소/환불 시나리오 통합 테스트 선행 필요
- **문서 불일치**: CLAUDE.md의 "유저 간 매칭 없음" 등 서비스 설명이 현재 기능(matching, ticket, mileage)과 어긋남 — 이번 정리와 함께 갱신 권장
