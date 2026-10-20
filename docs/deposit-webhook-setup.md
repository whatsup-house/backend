# 입금 알림 웹훅 설정 (KAN-403)

은행 입금 알림 원문을 와썹 백엔드로 보내면, 서버가 이름·금액을 파싱해 결제 대기 건과 자동 대사한다.
시크릿 값은 이 문서에 적지 않는다. 값은 `backend/.env`의 `DEPOSIT_WEBHOOK_SECRET`(GitHub Secret과 동일)에서 확인한다.

## 공통

- URL: `https://api.whatsup.house/api/payments/deposit-webhook`
- Method: `POST`
- Header: `Content-Type: application/json`, `X-Deposit-Secret: <DEPOSIT_WEBHOOK_SECRET 값>`
- Body: `{"text": "<알림 원문 그대로>", "source": "MACRODROID"}` (아이폰은 `"IOS_SHORTCUT"`)
- 응답: 항상 200(중복 알림 포함). 시크릿이 틀리면 401, 본문 2KB 초과면 413.
- 결과는 운영자 화면 `/admin/payments` → 입금 알림 로그에서 확인(자동 완료 / 무시 / 예외함 / 파싱 실패).

우리WON뱅킹 푸시 원문 예(가명): `[입금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30`
`[입금]`이 아닌 알림(출금 등)은 기록만 하고 무시된다.

## 안드로이드 — MacroDroid

1. 매크로 추가 → **트리거**: 알림 → 알림 수신 → 앱: 우리WON뱅킹 → 내용 포함: `[입금]`.
2. **액션**: 웹 상호작용 → HTTP 요청
   - 방식 POST, URL 위 주소
   - 헤더 `X-Deposit-Secret` 추가
   - 본문 유형 JSON, 본문 `{"text": "<알림 텍스트 매직 텍스트>", "source": "MACRODROID"}` — 알림 텍스트는 MacroDroid 매직 텍스트(알림 본문)로 넣는다. 따옴표·줄바꿈이 섞일 수 있으니 MacroDroid의 JSON 이스케이프 옵션을 켠다.
3. 휴대폰 배터리 최적화에서 MacroDroid와 우리WON뱅킹을 제외한다(알림 누락 방지).
4. 기존 구글시트 POST 매크로는 끈다.

## 아이폰 — 단축어 자동화 (문자 알림)

아이폰은 다른 앱의 푸시를 읽을 수 없어 은행 **문자(SMS) 입출금 알림**(유료)을 신청해야 한다.

1. 단축어 → 자동화 → 새 자동화 → **메시지** → 메시지 포함 `입금`(필요하면 발신자 지정) → **즉시 실행**.
2. 동작: **URL의 콘텐츠 가져오기**
   - URL 위 주소, 방법 POST
   - 헤더 `Content-Type: application/json`, `X-Deposit-Secret`
   - 요청 본문 JSON: `text` = 단축어 입력(메시지 내용), `source` = `IOS_SHORTCUT`
3. 문자 문구는 앱 푸시와 형식이 달라 서버 파서에 문자용 정규식이 추가되기 전까지는 **파싱 실패 → 예외함**으로 간다. 첫 문자 원문을 운영자 로그에서 복사해 개발 쪽에 전달하면 정규식을 추가한다.

## 수동 확인 (curl)

```bash
curl -s -X POST https://api.whatsup.house/api/payments/deposit-webhook \
  -H "Content-Type: application/json" \
  -H "X-Deposit-Secret: $DEPOSIT_WEBHOOK_SECRET" \
  -d '{"text":"[입금] 테스트        1원 1002-000-000***계좌 잔액 1원 10/20 12:00:00","source":"MACRODROID"}'
```

후보가 없으므로 결과는 `NO_CANDIDATE`(예외함)여야 정상이다. 확인 후 예외함 건은 해결 처리한다.

## 장애 시

- 폰이 꺼지거나 알림을 놓치면 결제 대기로 남는다 → 운영자가 `/admin/tickets` 입금 확인 또는 예외함 "신청에 연결"로 처리.
- 같은 이름·같은 금액 대기 건이 2개 이상이면 자동 처리하지 않고 예외함으로 간다.
