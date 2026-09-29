# 우연한 식탁 v2 설계 (해커톤 PRD 대응)

작성일: 2026-09-29
근거 문서: `우연한식탁_앱해커톤_안내_및_PRD.pdf` v0.2
관련 설계: `docs/chat-design.md`(채팅), `docs/domain-refactoring-proposal.md`(도메인 경계 규칙)
대상 레포: `whatsup-house/backend`, `whatsup-house-frontend`

## 0. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 범위 | PRD 전체. 참가자 기능은 기존 whatsup.house 앱 안에 탑재 |
| 프로필 | 폼 모델 유지(B안). `form_questions.reserved_key`로 표준 항목 고정 |
| 모임 구조 | **전 타입 공통** 리팩터링: `Gathering`(종류) + `GatheringSession`(회차). 이름이 같은 모임은 하나의 종류 |
| 가격 | 종류에 기본 가격, 회차에서 오버라이드 |
| 확정 | 자동 확정 + 유예 창(`PROPOSED` → 유예 후 `CONFIRMED`). 유예 0이면 즉시 |
| 매칭 단위 | 회차별 마감시각에 각각 실행 |
| 단체방 | 별도 진행 중인 앱 내 실시간 채팅(`chat-design.md`) 연동 |
| 알림 | 앱 내 알림만(기존 `notification` 도메인 재사용). 이메일·알림톡 없음 |
| 결제 | 이용권 모델 유지. 환불은 모의 상태 전환 |
| 식당 | 지역별 식당 풀 + 회차별 수용 테이블 수 |
| 피드백·안전 | 최소 세트(만족도 3항목, 재참여 의향, 사람별 다시/피하고 싶음, 신고) |
| 어드민 | 회차 중심 콘솔 3페이지 + 설정 1. 기존 `/admin/matching` 삭제 |

## 1. 현재 상태와 문제

- `Gathering` 한 엔티티에 날짜·시간·장소·정원·가격·상태가 붙어 있어 같은 이름의 모임을 프론트가 제목 문자열로 묶음(`gatheringGroup.ts`).
- 매칭은 `domain/matching`의 `MatchingEngine`(rule-v1)이 관리자 수동 실행. 하드 조건은 질문 키가 `age`/`budget`/`available_dates`일 때만 동작하는 매직 키.
- 확정 후 아무 후속 동작 없음. 참가자는 자기 테이블·식당을 볼 수 없음.
- 재배치·분리병합·대체 일정·환불 상태·예외함·피드백 반영 없음.

## 2. 도메인 모델

### 2.1 모임 종류 / 회차 (전 타입 공통)

```
gatherings (종류)                       gathering_sessions (회차)
  id, title, description, how_to_run,     id, gathering_id FK, event_date, start_time, end_time,
  tags, thumbnail_url, gathering_type,     location(기존 Location 임베디드; RANDOM_TABLE은 region만),
  form_id, base_price, is_curated,         max_attendees, price_override NULL, apply_deadline_at,
  curated_rank, created_at, updated_at     status (OPEN|CLOSED|DONE|CANCELLED),
                                           -- RANDOM_TABLE 전용, 그 외 NULL
                                           match_run_at, auto_confirm_grace_minutes NULL(설정 기본값 사용),
                                           table_size_min DEFAULT 4, table_size_max DEFAULT 6,
                                           min_group_score NULL, max_age_gap NULL
  UNIQUE(title) 는 두지 않음(관리자가 중복 생성 시 경고만)
```

`Gathering`에서 제거: `eventDate, startTime, endTime, location, price, maxAttendees, status`.

### 2.2 신청

```
applications
  + session_id UUID NULL        -- 배정 회차. REGULAR는 신청 시 즉시 채움
  + match_status VARCHAR(20)    -- RANDOM_TABLE만. 아래 상태값 참조
application_candidate_sessions
  application_id, session_id, priority INT   -- 희망 회차. REGULAR는 1행
```

결제(이용권 차감)는 신청에 붙어 있으므로 회차 이동 시 결제 유지(STA-06).

### 2.3 표준 폼 (B안)

`form_questions.reserved_key VARCHAR(30) NULL`. enum `ReservedQuestionKey`:

| key | 타입 | 매칭 사용 |
|---|---|---|
| BIRTH_YEAR | number | 하드: 테이블 내 최대-최소 ≤ maxAgeGap |
| GENDER | single | 페널티: 동성 −0.30, 소수 성별 1명 −0.10 |
| MBTI | single | 페어: 궁합표 lookup(0/0.5/1) |
| INTERESTS | multi | 페어: Jaccard |
| MY_STYLE | multi | 상대 WANTED_STYLE과 대조 |
| WANTED_STYLE | multi | 페어: 내 WANTED ∩ 상대 MY / 내 WANTED |
| DIET | text | 매칭 미사용, 식당 배정 참고·테이블 상세 표시 |

- 시드 마이그레이션으로 "우연한 식탁 표준 폼" 생성. `reserved_key`가 있는 질문은 어드민 폼 빌더에서 삭제·타입 변경 불가, 라벨·선택지만 수정.
- `reserved_key`가 없는 질문은 기존 `is_matching_field + matching_strategy(SAME/DIVERSE/OVERLAP) + matching_weight`로 선호 점수에 참여. 기존 매직 키(`age`, `budget`, `available_dates`) 로직은 삭제.
- 날짜·지역은 폼이 아니라 회차 선택으로 이동.
- 프로필 재사용(ACC-06): 신청 폼 진입 시 같은 유저의 가장 최근 `ApplicationAnswer`를 프리필. 별도 프로필 테이블 없음.

### 2.4 매칭 결과 (`domain/matching` 개편)

```
match_runs
  id, session_id, triggered_by (SCHEDULED|MANUAL), triggered_user_id NULL, started_at, finished_at,
  candidate_count, table_count, split_count, merge_count, reallocated_count, unassigned_count,
  algorithm_version, unassigned_reasons JSONB  -- [{applicationId, reason}]
  UNIQUE(session_id, started_at)

dining_tables  (← matching_groups 개명)
  id, session_id, match_run_id, status (PROPOSED|CONFIRMED|DONE|DISSOLVED),
  group_score NUMERIC(5,4), score_detail JSONB {pairAvg, pairMin, penalties:{...}},
  confirm_at TIMESTAMP NULL, confirmed_at NULL, venue_id NULL, chat_room_id NULL,
  reallocation_log JSONB, locked BOOLEAN DEFAULT false, created_at

dining_table_members  (← matching_members 개명)
  id, table_id, application_id, seat_order, assign_reason (INITIAL|REALLOCATED|MANUAL|SPLIT|MERGED),
  is_manual BOOLEAN, created_at
  UNIQUE(table_id, application_id)
  -- "활성(PROPOSED|CONFIRMED) 테이블에 한 신청은 1개만"은 서비스에서 검사

match_resolutions
  id, application_id, offered_session_ids JSONB, choice (TRANSFER|KEEP_TICKET|REFUND) NULL,
  chosen_session_id NULL, respond_by TIMESTAMP, status (OFFERED|RESOLVED|EXPIRED), resolved_at

exception_cases
  id, type (PAYMENT|DATA|VENUE|NOTIFICATION|SAFETY|CONFLICT|REFUND),
  session_id NULL, table_id NULL, application_id NULL, reason TEXT,
  status (OPEN|RESOLVED), resolved_by NULL, resolution_note TEXT, created_at, resolved_at
```

### 2.5 식당

```
venues            id, name, address, map_url, price_range, region, is_active
session_venues    session_id, venue_id, capacity_tables INT, used_tables INT DEFAULT 0
                  PK(session_id, venue_id)
```

### 2.6 참석·피드백·안전 (최소 세트)

```
attendances       id, table_member_id UNIQUE, status (SCHEDULED|ATTENDED|CANCELED_EARLY|CANCELED_LATE|NO_SHOW),
                  checked_in_at NULL, updated_by NULL
feedbacks         id, table_member_id UNIQUE, table_score 1-5, talk_score 1-5, venue_score 1-5,
                  rejoin_intent (YES|MAYBE|NO), comment TEXT NULL, created_at
peer_preferences  id, from_user_id, to_user_id, kind (AGAIN|AVOID), table_id, created_at
                  UNIQUE(from_user_id, to_user_id, table_id)
safety_reports    id, reporter_id, reported_user_id, table_id, reason TEXT,
                  status (OPEN|RESOLVED), action (NONE|WARN|RESTRICT|BAN) NULL, admin_note, created_at
```

`RandomTableEligibility`에 `RESTRICTED` 추가(운영자 조치 RESTRICT/BAN 시).

### 2.7 매칭 규칙 설정

```
matching_rule_settings (단일 행)
  max_age_gap DEFAULT 8, table_size_min 4, table_size_max 6, min_group_score DEFAULT 0.35,
  auto_confirm_grace_minutes DEFAULT 120,
  weights JSONB {gender, mbti, interests, wantedStyle, custom}
```
회차의 동명 컬럼이 NULL이 아니면 회차 값 우선.

### 2.8 알림

기존 `domain/notification`(`Notification` 엔티티, 이벤트 리스너, `/api/notifications`) 재사용. `NotificationType`과 `NotificationLink`에 아래 값 추가:

| 시점 | type | link |
|---|---|---|
| 결제 대기 | DINING_PAYMENT_PENDING | 이용권 구매 |
| 결제 완료 | DINING_WAITING | 내 신청 |
| 재배치 중 | DINING_REALLOCATING | 내 신청 |
| 매칭 확정 | DINING_CONFIRMED | 테이블 상세 |
| 매칭 실패 | DINING_ALTERNATIVE_OFFERED | 해결 선택 |
| 회차 이동 완료 | DINING_TRANSFERRED | 내 신청 |
| 환불 요청/완료 | DINING_REFUND_* | 내 신청 |
| 행사 1일 전 | DINING_REMINDER | 테이블 상세 |
| 행사 종료 후 | DINING_FEEDBACK_REQUEST | 피드백 |
| 다음 모집 | DINING_NEXT_SESSIONS | 모임 상세 |

발송 실패 3회 재시도 후 `ExceptionCase(NOTIFICATION)`. 발송 이력은 `Notification` 행 자체.

## 3. 상태값

- 신청 `status`: 기존 `ApplicationStatus` 유지.
- 결제: 기존 이용권 차감 레코드에 상태 추가 → `DEDUCTED | RESTORED | REFUND_REQUESTED | REFUND_PROCESSING | REFUNDED | REFUND_FAILED`. PRD `payment_status`는 이것과 신청 status의 조합으로 표현.
- 매칭 `application.match_status`: `WAITING → MATCHING → CONFIRM_PENDING → CONFIRMED`, 분기 `REALLOCATING`, `ALTERNATIVE_OFFERED → TRANSFERRED(→WAITING) | NO_MATCH`, `EXCEPTION`.
- 테이블 `dining_tables.status`: `PROPOSED → CONFIRMED → DONE`, `DISSOLVED`.
- 참석 `attendances.status`: `SCHEDULED | ATTENDED | CANCELED_EARLY | CANCELED_LATE | NO_SHOW`.

상태 전이는 각각 엔티티 메서드로만 변경하고 `updated_at`·변경 주체를 남긴다(PRD 16장 추적).

## 4. 매칭 엔진 v2 (`rule-v2`)

### 4.1 트리거

- `DiningMatchScheduler`: 1분 주기. `status=OPEN AND match_run_at <= now()`인 회차를 `SELECT ... FOR UPDATE SKIP LOCKED`로 잡고 `CLOSED`로 전환 후 `runMatching(session, SCHEDULED)`.
- 수동: `POST /api/admin/dining/sessions/{id}/match-runs` (MANUAL). 마감 전에도 가능.
- 재실행 규칙: `PROPOSED`이고 `locked=false`인 테이블만 `DISSOLVED` 처리 후 멤버를 후보로 복귀. `CONFIRMED`와 `locked=true`(수동 조정 포함)는 유지.

### 4.2 후보

해당 회차가 `application_candidate_sessions`에 있고, `status=CONFIRMED`(이용권 차감 완료), `match_status ∈ {WAITING, REALLOCATING}`, 활성 테이블 없음, `randomTableEligibility=APPROVED`. 결제 미완료는 제외하고 `DINING_PAYMENT_PENDING` 알림.

### 4.3 하드 조건 (완화 없음)

1. 테이블 인원 `[tableSizeMin, tableSizeMax]`
2. 출생연도 최대-최소 ≤ `maxAgeGap`
3. 멤버 쌍 중 `peer_preferences(AVOID)`, `safety_reports`(양방향), 채팅 뮤트 관계 없음

### 4.4 점수

- 페어 점수 = Σ(weight × 항목 점수) / Σweight. 항목: GENDER DIVERSE(다르면 1), MBTI 궁합표, INTERESTS Jaccard, WANTED_STYLE 일치율(양방향 평균), 커스텀 질문(전략별).
- 그룹 점수 = 0.7·pairAvg + 0.3·pairMin − 페널티. 페널티: 전원 동성 0.30, 소수 성별 1명 0.10, 이전 같은 테이블 페어당 0.15(MAT-06), 같은 직업군 3명+ 0.05, 같은 MBTI 3명+ 0.05. 0 미만은 0.
- `score_detail` JSON에 항목별 값을 저장해 어드민에 그대로 노출(PRD 7.2).

### 4.5 그룹 형성

1. 후보 풀에서 그리디 시드 방식(기존 `hardestToPlace` 유지)으로 `tableSizeMax` 이하 테이블 생성. 채우는 기준은 평균 페어 점수, 단 하드 조건 통과 필수.
2. **분리**: 7명 이상 한 덩어리는 만들지 않음(최대 6에서 자름). 남은 인원이 `tableSizeMin` 미만이면 미완성 테이블로 보류.
3. **병합**: 미완성 테이블끼리 합쳐 `[min,max]`에 들면서 하드 조건 통과하면 병합(MERGED).
4. **재배치**: 그래도 남은 사람은 `PROPOSED` 테이블 중 `< tableSizeMax`이고 하드 조건 통과하는 곳에 점수 순 삽입(REALLOCATED). 실패 시:
   - 다음 희망 회차가 남아 있으면 `REALLOCATING`(그 회차 실행 시 후보로 포함).
   - 마지막 희망 회차였으면 `ALTERNATIVE_OFFERED` + `MatchResolution` 생성. 제안 회차 = 신청자가 고르지 않은 OPEN 회차 중 마감 전인 것(PRD 7.4의 5번). 없으면 `NO_MATCH`로 두고 KEEP_TICKET/REFUND만 제안.
5. **검증**(MAT-07): 생성된 모든 테이블을 하드 조건으로 재검증. 실패 테이블은 해체 후 4번 루프.
6. `min_group_score` 미달 테이블은 해체 후 4번 루프(멤버는 재배치 대상).
7. 살아남은 테이블 → `PROPOSED`, `confirm_at = now + grace`, 멤버 `match_status=CONFIRM_PENDING`.
8. `MatchRun` 집계 저장. 미배정 사유는 `AGE_GAP | BLOCKED_PAIR | NOT_ENOUGH_PEOPLE | LOW_SCORE | NEXT_SESSION_WAITING` 중 하나.

### 4.6 자동 확정 파이프라인

`DiningConfirmScheduler`: 1분 주기, `PROPOSED AND confirm_at <= now()`인 테이블을 개별 트랜잭션으로 처리.

1. 하드 조건 최종 검증(수동 조정 후 위반이 남아 있으면 확정 보류 + `ExceptionCase(CONFLICT)`).
2. `CONFIRMED`, 멤버 `match_status=CONFIRMED`, `Attendance(SCHEDULED)` 생성. **여기까지가 트랜잭션. 이후 단계는 실패해도 확정 유지.**
3. 식당 배정: 회차 `session_venues` 중 `used < capacity`인 것을 순서대로. 없으면 `ExceptionCase(VENUE)`.
4. 채팅방 생성: `ChatRoomService.createGroupRoom(name, memberIds, sourceType=DINING_TABLE, sourceId=tableId)` 호출(`chat-design.md`의 관리자 방 생성과 동일 서비스; `source_type`에 `DINING_TABLE` 추가). 시스템 공지 1건 게시(시간·지역·식당·취소 정책). 실패 시 `ExceptionCase(NOTIFICATION)`.
5. 멤버 알림 `DINING_CONFIRMED`.

운영자 "즉시 확정"은 `confirm_at = now`로 당기는 것뿐, 파이프라인은 동일.

### 4.7 확정 후 취소 (PRD 15장)

멤버 취소(`Attendance.CANCELED_EARLY`, 이용권 복원) 시 `DiningTableService.rebalance(table)`:
- 남은 인원 ≥ `tableSizeMin`: 그대로.
- 미만: 같은 회차 `REALLOCATING` 대기자 중 하드 조건 통과자를 점수 순으로 충원. 없으면 남은 멤버를 같은 회차의 다른 `< max` 테이블로 재배치. 그것도 안 되면 `ExceptionCase(CONFLICT)`(운영자가 병합·해체 결정).
- 충원·이동된 멤버는 채팅방 멤버 갱신 + 알림.

### 4.8 참가자 해결 선택 (`MatchResolution`)

- `TRANSFER`: `chosen_session_id`를 희망 회차로 교체, `match_status=WAITING`, 결제 유지, 알림 `DINING_TRANSFERRED`.
- `KEEP_TICKET`: 이용권 `RESTORED`, 신청 `CANCELLED`.
- `REFUND`: 이용권 구매 건 `REFUND_REQUESTED → REFUND_PROCESSING → REFUNDED`(모의, 즉시). 실패 시 `REFUND_FAILED` + `ExceptionCase(REFUND)`.
- `respond_by`(기본 48시간) 경과 시 `EXPIRED` 처리 후 `KEEP_TICKET`과 동일하게 자동 처리.
- 동일 신청에 대해 처리 중 중복 요청은 `status=OFFERED`일 때만 허용.

### 4.9 리마인드·콘텐츠·체크인

- `DiningReminderScheduler`: 회차 시작 24시간 전 `CONFIRMED` 테이블 멤버에게 `DINING_REMINDER` + 채팅방 시스템 메시지.
- 회차 시작 시각: 채팅방에 대화 주제 3개 + 아이스브레이킹 1개 게시. 콘텐츠는 `dining_contents(kind, interest_tag NULL, body)` 시드 테이블에서 테이블 공통 관심사 우선, 없으면 범용. 관리 UI 없음(CON-04는 P1).
- 체크인: `POST /api/dining/tables/{id}/check-in`, 회차 시작 ±2시간만 허용 → `ATTENDED`.
- 회차 종료 +1시간: 체크인 없는 `SCHEDULED`를 `NO_SHOW` 후보로 표시(상태는 그대로), 운영자가 콘솔에서 확정. 동시에 테이블 `DONE`, 멤버에 `DINING_FEEDBACK_REQUEST`.

### 4.10 예외함으로 가는 것 (전부 자동 처리 불가 건만)

| type | 발생 |
|---|---|
| PAYMENT | 결제 데이터 불일치(이용권 차감 실패 등) |
| DATA | 필수 답변 누락으로 하드 조건 평가 불가 |
| VENUE | 식당 수용 부족 |
| NOTIFICATION | 알림·채팅방 생성 3회 실패 |
| CONFLICT | 확정 후 취소 미해결, 수동 조정 후 하드 조건 위반 |
| REFUND | 자동 환불 실패 |
| SAFETY | 신고 접수 |

## 5. API

### 5.1 공통 (모임 종류/회차)

```
GET    /api/gatherings                         종류 목록 + 각 종류의 다가오는 회차 요약
GET    /api/gatherings/{id}                    종류 상세 + 회차 목록
GET    /api/gatherings/sessions/{sessionId}    회차 상세 (옛 /gatherings/{id} 링크는 프론트에서 종류 페이지로 리다이렉트)
POST   /api/applications                       {gatheringId, candidateSessionIds[], answers}
                                               REGULAR: 1개 필수 → session_id 즉시 세팅
관리자
POST/PUT/DELETE /api/admin/gatherings/{id}
POST   /api/admin/gatherings/{id}/sessions     단건 또는 {repeatWeekly: {until}} 반복 생성
PUT/DELETE /api/admin/gatherings/sessions/{sessionId}
```

### 5.2 우연한 식탁 참가자 (`/api/dining`)

```
GET    /me/applications                       내 신청 + match_status + 배정 테이블 요약
GET    /tables/{id}                           테이블 상세(멤버 제한 소개, 식당, 채팅방, 콘텐츠, 취소 정책)
POST   /tables/{id}/check-in
POST   /applications/{id}/cancel              사전 취소 → 4.7
GET    /resolutions/{id}  POST /resolutions/{id}/choose {choice, sessionId?}
POST   /tables/{id}/feedback                  {tableScore, talkScore, venueScore, rejoinIntent, comment, peers:[{userId, kind}]}
POST   /tables/{id}/reports                   {reportedUserId, reason}
GET    /me/history                            과거 회차·식당·피드백 여부
```

### 5.3 운영자 (`/api/admin/dining`)

```
GET    /dashboard                             타일 수치 + 다가오는 회차 카드
GET    /sessions/{id}/applicants              표 + CSV(?format=csv)
POST   /sessions/{id}/match-runs              수동 실행/재실행
GET    /sessions/{id}/tables                  테이블 + 미배정(사유 포함) + 최신 MatchRun 집계
POST   /tables/{id}/members/{memberId}/move   {targetTableId}     → 재검증, locked=true
POST   /tables/{id}/split                     {memberIds[]}       → 새 테이블
POST   /tables/merge                          {tableIds[]}
POST   /tables/{id}/confirm-now
POST   /tables/{id}/dissolve
PUT    /tables/{id}/venue                     {venueId}
POST   /tables/{id}/chat-room/retry
POST   /tables/{id}/notifications/retry
GET    /sessions/{id}/attendance   PATCH /attendances/{id} {status}
GET    /sessions/{id}/feedback-summary
GET    /exceptions?type=&status=   PATCH /exceptions/{id} {status, note, action?}
GET/PUT /settings/matching-rules
GET/POST/PUT/DELETE /venues        PUT /sessions/{id}/venues [{venueId, capacityTables}]
```

수동 조작은 모두 `CONFIRMED` 테이블에는 "긴급 수정" 플래그와 사유 필수(MAT-14), 이력은 `reallocation_log`에 남김.

## 6. 운영자 어드민 (프론트)

### `/admin/dining` 운영 대시보드
- 타일: 다가오는 회차, 대기 신청자, 결제 완료, 제안 중 테이블, 확정 테이블, 열린 예외.
- 회차 카드 목록(날짜·지역·결제완료/정원·실행 예정/완료·테이블 수·예외 수·상태). 클릭 → 콘솔.
- "회차 만들기" 모달(날짜·시간·지역·정원·마감·실행시각·유예·식당 풀·주간 반복).

### `/admin/dining/sessions/[id]` 회차 콘솔
- 헤더: 회차 정보·상태·액션(마감 수정 / 지금 실행 / 재실행 / 전체 즉시 확정 / 회차 취소).
- 탭 **신청자**: 이름·나이·성별·MBTI·결제·매칭 상태·희망 회차·참가 횟수. 결제 미완료·제외 관계 행 강조. CSV.
- 탭 **테이블**: 카드(상태 + "N분 후 확정" 카운트다운, 점수 + breakdown 툴팁, 멤버 칩). 드래그앤드롭 이동(`@dnd-kit` 이미 의존성에 있으면 사용, 없으면 네이티브 HTML5 DnD), 카드 메뉴 분리·병합·즉시 확정·해체. 조작 즉시 하드 조건 재검증 → 위반 시 카드 빨간 경고, 확정 차단. 하단 미배정 목록 + 사유.
- 탭 **식당·단체방**: 테이블별 식당 드롭다운, 채팅방 생성 상태·재시도, 알림 이력·재발송.
- 탭 **참석·피드백**: 체크인 현황, 노쇼 후보 확정, 만족도 평균, 신고 건 링크.

### `/admin/dining/exceptions` 예외함
- 전체 회차 횡단. 유형 필터, 열림/해결 탭. 건별 컨텍스트 액션(식당 선택 / 재발송 / 환불 수동 완료 / 신고 조치 경고·제한·영구제한). 처리 메모 필수.

### `/admin/dining/settings`
- 매칭 규칙 기본값, 식당 풀 CRUD.

### 기존 페이지
- `/admin/matching` 삭제. 사이드바 "우연한 식탁 매칭" → "우연한 식탁"(`/admin/dining`).
- `/admin/gatherings`: 종류 CRUD + 종류 상세 안에 회차 목록·추가(타입에 따라 전용 필드 노출).
- 폼 빌더: `reserved_key` 질문 잠금 표시.

## 7. 참가자 화면 (`(main)`)

- 모임 목록·모아보기: `gatheringGroup.ts` 삭제, 백엔드 응답(종류 + 회차)을 그대로 사용. `/gatherings/[id]`는 종류 페이지, 회차 목록에서 날짜·지역 선택. 옛 회차 ID로 진입 시 종류 페이지로 리다이렉트.
- 우연한식탁 신청: 회차 복수 선택 → 표준 폼(프리필) → 이용권 확인/구매(기존 `/payments/random-table`) → 제출.
- 내 신청 상태 카드(홈·마이페이지): 결제 → 매칭 대기(실행 예정 시각) → 매칭 중 → 재배치 중 → 확정 → 참석 완료. 실패 시 카드 안에서 대체 회차 / 이용권 보관 / 환불 선택.
- 테이블 상세: 날짜·지역·식당(지도·가격대)·구성원 제한 소개(닉네임·MBTI·관심사 태그만)·대화 주제·채팅방 버튼·취소 정책·체크인 버튼.
- 행사 후 피드백 폼. 알림함은 기존 `/api/notifications` UI 재사용. 참가 이력 페이지.

## 8. 마이그레이션 (Flyway V6 이후, 채팅 V5 다음)

1. `gathering_sessions` 생성. 기존 `gatherings` 각 행 → 같은 `id`로 세션 행 복사(날짜·시간·장소·정원·가격→`price_override`·상태).
2. `gatherings`를 제목 기준으로 그룹화. 그룹마다 대표 행(가장 이른 `created_at`) 하나를 종류로 남기고 나머지 행은 삭제. 세션의 `gathering_id`는 대표 행 ID로. 폼·큐레이션·썸네일은 대표 행 값.
3. `applications.session_id = 옛 gathering_id`, `gathering_id = 대표 행 ID`. `application_candidate_sessions`에 1행씩.
4. `matching_groups → dining_tables`, `matching_members → dining_table_members` 개명 + 신규 컬럼. 기존 `gathering_id` → `session_id`.
5. `chat_rooms.source_type`에 `DINING_TABLE` 허용, 기존 `MATCHING_GROUP` 값은 `DINING_TABLE`로 갱신.
6. 표준 폼 시드, `matching_rule_settings` 1행, `dining_contents` 시드.
7. `gatherings`에서 제거된 컬럼 drop은 애플리케이션 배포 후 별도 마이그레이션(롤백 여지).

주의: 제목이 같지만 실제로 다른 모임이 있으면 하나로 합쳐짐. 마이그레이션 전 제목 중복 목록을 뽑아 운영자 확인.

## 9. 도메인 경계

`domain-refactoring-proposal.md` D4 규칙 준수: 타 도메인 Repository 직접 주입 금지. `matching`은 `ApplicationService`, `TicketService`, `ChatRoomService`, `NotificationService`(이벤트 발행), `VenueService`를 서비스로 의존. 확정·실패·리마인드 등은 도메인 이벤트(`DiningTableConfirmedEvent` 등)로 발행하고 알림 리스너가 소비.

## 10. 테스트

- `MatchingEngineV2Test`: 하드 조건, 분리·병합(7·11·13명), 재배치 삽입, 최소 점수 해체, AVOID/신고 제외, 이전 만남 페널티, 결정적 시드로 재현성.
- `DiningMatchServiceTest`: 중복 실행 방지(락), 재실행 시 CONFIRMED·locked 보존, 마지막 회차 실패 → MatchResolution 생성.
- `DiningConfirmPipelineTest`: 식당 없음 → 확정 유지 + VENUE 예외, 채팅 실패 → 확정 유지 + NOTIFICATION 예외, 유예 0 즉시 확정.
- `DiningTableServiceTest`: 확정 후 취소 → 충원/재배치/CONFLICT, 수동 이동 시 하드 조건 위반 차단.
- `MatchResolutionServiceTest`: TRANSFER/KEEP_TICKET/REFUND, 만료 자동 처리.
- 마이그레이션 테스트(Testcontainers): 제목 같은 모임 3개 → 종류 1 + 회차 3, 신청 FK 보존.
- Playwright E2E: PRD 18장 데모 시나리오, 시드 참가자 12명, 스케줄러는 관리자 "지금 실행"·"즉시 확정"으로 대체.

## 11. 빌드 순서 (각 단계 = 스펙 확정 → 플랜 → 구현 → PR)

1. 모임 종류/회차 분리 리팩터링 + 마이그레이션 + 프론트 목록·상세 반영 (전 타입 영향, 최우선 안정화)
2. 표준 폼 `reserved_key` + 우연한식탁 신청 흐름(회차 복수 선택·프리필) + 이용권 연동 + `match_status`
3. 엔진 v2 + `MatchRun` + 스케줄러 + 자동 확정 파이프라인(식당·채팅·알림 단계 포함) + `MatchResolution` + 예외함 백엔드
4. 어드민 대시보드·회차 콘솔·예외함·설정
5. 참가자 상태 카드·테이블 상세·해결 선택·체크인·피드백·신고 + 리마인드·콘텐츠 스케줄러
6. E2E 데모 시나리오 + CSV 내보내기

## 12. 범위 밖 (PRD P1/P2 및 결정으로 제외)

실결제 PG(PAY-04), 대기자 자동 전환(STA-07), 학습형 가중치(MAT-16), 콘텐츠 관리 UI(CON-04), 이용권 관리 화면 개편(RET-03), 이메일·알림톡 채널, 마감시각 동일 회차 묶음 최적화.
