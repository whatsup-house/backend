# 와썹하우스 채팅 기능 설계

작성일 2026-09-29. 브레인스토밍으로 확정한 결정 사항과 설계. 백엔드 레포에도 같은 파일(`backend/docs/chat-design.md`)을 둔다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 단체방 | 관리자만 자유 생성. 생성 시 게더링 참가 확정자 / 매칭 조원 불러오기로 멤버 프리필 |
| 1:1 | 사용자당 "와썹하우스 공용 문의방" 1개. 모든 관리자가 보고 답함. 사용자에겐 상대가 "와썹하우스"로 보임 |
| 실시간 | STOMP over WebSocket (Spring 내장). 송신은 REST, 소켓은 수신 전용 |
| 암호화 | 서버측 AES-256-GCM, 키는 `CHAT_ENCRYPTION_KEY` 환경변수 |
| 메시지 종류 | 텍스트, 이미지(클라이언트에서 긴 변 1080px 리사이즈·압축), 링크 미리보기, 이모지 리액션 |
| 기록 | 나갔다 재초대되면 전체 기록 보임. 문의방은 나가기 불가, 목록 숨기기만 |
| 알림 | 바텀 내비·방 목록 안읽은 배지 + 웹 푸시 |
| 읽음 | 카톡식 안 읽은 사람 수 |
| 수정/삭제 | 본인 수정·삭제(소프트). 관리자는 타인 메시지 삭제 가능 |
| 공지 | 관리자만 등록/교체/해제. 방 최상단 고정 1개 |
| 입장/퇴장 | 초대·내보내기는 방 안 시스템 메시지로만 기록(알림·배지·안읽은 수 제외). "조용히 나가기"는 기록 없음 |
| 제재 | 멤버 내보내기, 채팅 금지(뮤트), 메시지 신고 |
| 타이핑 표시 | 없음 |
| 바텀 내비 | 게더링 · 빈칸(회색 비활성 아이콘) · 홈 · 채팅 · 마이 (5칸) |
| 테마 | 토글 없음. 내 말풍선 갈색 `#5C4033` 바탕 + 아이보리 `#F5F0EB` 글자, 상대 말풍선은 반전 |

건너뜀: 타이핑 표시, 메시지 검색, 답장/멘션, 이미지 외 파일 첨부, 키 로테이션, 다크모드.

## 1. 데이터 모델 (Flyway V4 — develop 기준 다음 번호, 병합 시점에 재확인)

```
chat_rooms
  id UUID PK, type VARCHAR(10) (INQUIRY | GROUP), name VARCHAR(100) NULL (GROUP만),
  source_type VARCHAR(20) NULL (GATHERING | MATCHING_GROUP), source_id UUID NULL,
  inquiry_user_id UUID NULL UNIQUE (INQUIRY: 문의한 사용자),
  notice_message_id UUID NULL, created_by UUID, created_at, updated_at, deleted_at NULL

chat_members
  id UUID PK, room_id, user_id, joined_at, left_at NULL,
  last_read_message_id UUID NULL, hidden BOOLEAN DEFAULT false,
  UNIQUE(room_id, user_id)

chat_messages
  id UUID PK, room_id, sender_id UUID NULL (SYSTEM은 NULL),
  type VARCHAR(10) (TEXT | IMAGE | SYSTEM), content_enc BYTEA, nonce BYTEA,
  system_kind VARCHAR(30) NULL (JOINED | KICKED | NOTICE_SET), system_params JSONB NULL,
  link_preview JSONB NULL, deleted_at NULL, edited_at NULL, created_at
  INDEX(room_id, created_at)

chat_reactions   message_id, user_id, emoji VARCHAR(8), PK(message_id, user_id, emoji)
chat_mutes       user_id PK, muted_by, reason TEXT, created_at
chat_reports     id, message_id, reporter_id, reason TEXT, status VARCHAR(10) (OPEN | RESOLVED), created_at
push_subscriptions  id, user_id, endpoint TEXT UNIQUE, p256dh TEXT, auth TEXT, created_at
```

- INQUIRY 방 멤버 = 문의 사용자 + 관리자 전원. 관리자가 관리자 방 목록을 조회할 때 미가입 문의방에 자동 등록(시스템 메시지 없음).
- GROUP 방의 `source_*`는 표시용. 자동 동기화 없음.
- 재초대는 `left_at`을 NULL로 되돌림. 메시지 조회는 `joined_at` 필터 없이 방 전체.
- 암호화 대상은 `content_enc` 하나. TEXT는 본문, IMAGE는 스토리지 경로, SYSTEM은 빈 값. `system_params`(닉네임 등 표시용)는 평문 JSONB.

## 2. 권한 규칙 — `ChatAccessPolicy` 하나에 모음

| 행위 | 일반 사용자 | 관리자 |
|---|---|---|
| 문의방 시작 | 본인 1개 자동 생성 | 사용자 목록에서 열기 |
| 단체방 생성 / 멤버 추가·내보내기 / 삭제 | ✗ | ✓ |
| 메시지 전송 | 멤버 & `left_at` NULL & 미뮤트 & 계정 정상 | 동일 |
| 본인 메시지 수정·삭제 | ✓ | ✓ |
| 타인 메시지 삭제 | ✗ | ✓ |
| 공지 등록/해제 | ✗ | ✓ |
| 조용히 나가기 | GROUP만 | GROUP만 |
| 문의방 숨기기 | ✓ | ✗ |
| 신고 | ✓ | 목록 처리 |
| 뮤트 | ✗ | ✓ (계정 정지와 별개) |

REST는 `/api/chat/**`(인증) + `/api/admin/chat/**`(ROLE_ADMIN). 기존 SecurityConfig 패턴.

## 3. 실시간 (STOMP)

- 엔드포인트 `/ws-chat`. SockJS 없음. 의존성 `spring-boot-starter-websocket` 1개.
- 인증: CONNECT 프레임 헤더 `Authorization: Bearer <JWT>`를 `ChannelInterceptor`에서 기존 JWT 검증기로 확인해 Principal 세팅. URL 토큰 금지.
- 구독: `/topic/rooms/{roomId}` (구독 시 멤버십 검사, 아니면 거부), `/user/queue/rooms` (내 방 목록 갱신).
- 클라이언트 SEND는 `/app/rooms/{id}/read` (읽음 처리) 하나. 나머지는 REST.
- 이벤트: `{ kind: MESSAGE_CREATED | MESSAGE_UPDATED | MESSAGE_DELETED | REACTION_CHANGED | READ | NOTICE_CHANGED | MEMBER_CHANGED, roomId, payload }`. 복호화된 본문 포함.
- 재접속: 클라이언트 지수 백오프, 재접속 시 `GET /messages?after={lastSeenId}`로 보충.
- 브로커: 인메모리 SimpleBroker. `// ponytail: 단일 인스턴스 전제, 스케일아웃 시 Redis pub/sub 브리지`.

## 4. 암호화 — `ChatCipher`

- AES-256-GCM, `javax.crypto`만. 메시지마다 12바이트 랜덤 nonce, 태그 128비트, `room_id`를 AAD로.
- 키: `CHAT_ENCRYPTION_KEY` (base64 32바이트). docker-compose.prod.yml 환경변수 추가. local/test 프로파일은 고정 더미 키.
- 복호화는 응답 DTO 조립 시에만. 로그·예외에 평문 금지.
- 이미지 파일은 Supabase 비공개 버킷 `chat-images`. DB엔 경로를 암호화 저장, 응답 시 서명 URL(1시간).

## 5. REST API

```
# 사용자 (/api/chat)
GET    /rooms                              내 방 목록(마지막 메시지, 안읽은 수, hidden 제외)
POST   /rooms/inquiry                      문의방 열기(없으면 생성, hidden 해제) → roomId
GET    /rooms/{id}                         방 상세(멤버, 공지, 내 권한)
GET    /rooms/{id}/messages?before=&after=&size=50
POST   /rooms/{id}/messages                {type: TEXT|IMAGE, content}
PATCH  /messages/{id}                      {content}  본인 TEXT만
DELETE /messages/{id}                      소프트 삭제(본인 또는 관리자)
PUT    /messages/{id}/reactions/{emoji}    토글
POST   /messages/{id}/report               {reason}
POST   /rooms/{id}/leave                   조용히 나가기(GROUP만)
POST   /rooms/{id}/hide                    문의방 숨김
POST   /upload-image                       multipart → {path}  (chat-images 버킷)
POST   /push-subscriptions / DELETE        {endpoint, p256dh, auth}

# 관리자 (/api/admin/chat)
POST   /rooms                              {name, memberIds[], sourceType?, sourceId?}
GET    /rooms                              전체 방(문의방 미답변 표시, 안읽은 수)
GET    /rooms/source-members?type=&id=     게더링 참가확정자 / 매칭 조원 → [{userId, nickname}]
POST   /rooms/{id}/members                 {userIds[]} → SYSTEM(JOINED)
DELETE /rooms/{id}/members/{userId}        내보내기 → SYSTEM(KICKED)
PUT    /rooms/{id}/notice                  {messageId | null}
DELETE /rooms/{id}                         방 소프트 삭제
PUT    /mutes/{userId} {reason} / DELETE   뮤트
GET    /reports?status= , PATCH /reports/{id} {status}
```

- 안읽은 수 = `last_read_message_id` 이후의 비SYSTEM 메시지 수. 시스템 메시지는 배지·푸시 제외.
- 시스템 메시지 텍스트는 클라이언트가 `system_kind` + `system_params`로 5개 언어 렌더. 백엔드 i18n 없음.
- 링크 미리보기: 메시지 저장 시 본문의 첫 URL을 비동기로 OG 조회 → `link_preview` `{url,title,description,image}` 채우고 `MESSAGE_UPDATED` 브로드캐스트. 사설 IP·localhost·리다이렉트 5회 초과 차단, 타임아웃 3초, 응답 512KB 제한, 결과 Caffeine 캐시 1시간.
- 제한: 텍스트 2000자, 이미지 5MB, 리액션 화이트리스트 `👍 ❤️ 😂 😮 😢 🙏`.
- 레이트리밋: 기존 `RateLimitFilter`에 메시지 전송 초당 2건.
- 에러코드 추가: `CHAT_NOT_MEMBER`(403), `CHAT_MUTED`(403), `CHAT_ROOM_TYPE_MISMATCH`(400), `CHAT_ROOM_NOT_FOUND`(404), `CHAT_MESSAGE_NOT_FOUND`(404).

## 6. 프론트엔드

**바텀 내비** (`src/components/layout/BottomNav.tsx`)
- `grid-cols-5`: 게더링(Compass) · 빈칸(회색 `Circle`, `aria-hidden`, 클릭 없음) · 홈(Home) · 채팅(MessageCircle, 로그인 필요) · 마이.
- 채팅 아이콘 우상단 안읽은 총합 배지(primary, 99+). 값은 `GET /api/chat/rooms` 합계, react-query 30초 폴링 + 소켓 `/user/queue/rooms` 수신 시 즉시 갱신.
- `HIDDEN_PATTERNS`에 `/^\/chat\/[^/]+$/` 추가. `nav.tabs.chat` 5개 언어.

**라우트 `src/app/(main)/chat/`**
- `page.tsx` 방 목록: 카톡 채팅 탭 레이아웃. 상단 고정 "와썹하우스에 문의하기" 행. 아바타 · 방 이름 + 멤버 수 · 마지막 메시지 1줄 · 시간 · 안읽은 배지. 길게 누르기: 문의방 "숨기기", 단체방 "조용히 나가기".
- `[id]/page.tsx` 채팅방: 헤더(뒤로, 방 이름, 멤버 수, 햄버거 → 멤버 드로어). 공지 바(접기/펼치기, 관리자면 해제). 위로 스크롤 시 `before` 커서로 50건 추가. 하단 고정 입력창(+ 이미지, 텍스트, 전송). 말풍선 길게 누르기 → 액션 시트(리액션 6개 / 복사 / 수정·삭제(본인) / 공지 등록(관리자) / 신고).

**말풍선**
- 내 메시지: 배경 `#5C4033`(tag-text), 글자 `#F5F0EB`(background), 우측 정렬.
- 상대 메시지: 배경 `#F5F0EB`, 글자 `#5C4033`, 아바타+닉네임(연속 메시지는 첫 번째만). 문의방에서 관리자 메시지의 표시명은 "와썹하우스".
- 시스템 메시지·날짜 구분선: 중앙 pill, `tag-bg`.
- 말풍선 옆 시간 + 안읽은 수(primary 소형). "(수정됨)", "삭제된 메시지입니다".
- 방 배경 `card` 흰색, 말풍선 radius 12px. 테마 토글 없음.

**통신**
- `src/lib/api/chat.ts`, `src/lib/hooks/useChat.ts` (react-query). 소켓 이벤트로 `setQueryData` 갱신.
- `@stomp/stompjs` 1개 추가. `src/lib/chat/socket.ts`: 로그인 상태 연동, `/chat` 라우트에서만 연결, 지수 백오프 재접속, 재접속 시 `after` 보충 조회.
- 이미지: `<input type="file" accept="image/*">` → canvas 긴 변 1080px → `toBlob('image/webp', 0.8)` (미지원 시 jpeg) → 업로드. 라이브러리 없음.
- 낙관적 전송: 임시 ID 즉시 표시 → 응답으로 교체, 실패 시 재전송 버튼.

**관리자 `src/app/admin/chat/`**
- 방 목록(문의방 미답변 필터 / 단체방) · 방 생성 폼(이름, 게더링·조 선택 시 멤버 프리필 + 닉네임 검색 추가) · 방 화면은 사용자용 `[id]` 재사용 + 관리자 액션(멤버 추가/내보내기, 공지, 타인 메시지 삭제) · 신고 목록 · 뮤트 목록.

## 7. 웹 푸시 (마지막 단계)

- BE: `nl.martijndwars:web-push` 1개. `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` 환경변수. 발송 조건: 방 멤버 & 해당 방 소켓 미구독 & 비SYSTEM. 메시지마다 1건, 클릭 시 `/chat/{roomId}`. 410/404 응답이면 구독 삭제.
- FE: `public/sw.js`, 채팅 탭 첫 진입 시 권한 요청 배너, iOS는 홈화면 추가 시에만 동작 안내.

## 8. 엣지

- 멤버 아닌 방 구독 → STOMP ERROR로 거부.
- 뮤트: 입력창 비활성 + 안내, 서버도 거부.
- 정지·탈퇴 사용자: 소켓 거부, 방 목록 제외, 방 안 표시명 "(탈퇴한 회원)".

## 9. 테스트

- BE: `ChatCipherTest`(왕복, AAD 불일치 실패), `ChatAccessPolicyTest`(권한표 전수), `ChatServiceTest`(문의방 단일성, 재초대 전체 기록, 시스템 메시지 안읽은 수 제외), `LinkPreviewFetcherTest`(사설 IP 차단), WebSocket 통합 테스트 1개(구독 거부, 메시지 수신). H2 테스트 프로파일.
- FE: Playwright "관리자 방 생성 → 사용자 접속 → 송수신 → 공지 등록" 1개.

## 10. 구현 단계 (PR 단위)

1. BE 코어: 마이그레이션, 엔티티, 암호화, 권한, REST, 테스트
2. BE 실시간: STOMP, 인터셉터, 브로드캐스트
3. BE 링크 미리보기
4. FE 바텀 내비 5탭
5. FE 방 목록·채팅방 화면
6. FE 소켓 연동
7. FE 관리자 화면
8. BE 웹 푸시
9. FE 웹 푸시
