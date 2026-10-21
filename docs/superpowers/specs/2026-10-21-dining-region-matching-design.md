# 우연한 식탁 — 회차×지역 매칭 · 식당 자동 배정 · 매칭 근거 설계

- 작성일: 2026-10-21
- 근거: 해커톤 PRD v0.2 (APP-02, MAT-02·03·07·08·12, INF-02~05, 7.4 재배치 우선순위), CTO 온보딩 9쪽(매칭 근거 그래프)
- 기준 코드: develop `c2929f7` (BE), `fc5aeb5` (FE). 최신 Flyway V27.

## 0. 바뀌는 것 한눈에

| | 지금 | 바꾼 뒤 |
|---|---|---|
| 회차 | 회차 1개 = 장소(Location) 1개 = 지역 1개 | 회차는 날짜·시간만. 모든 회차에 서울 전 지역(4곳)이 열린다 |
| 신청 | 희망 회차(지망 순) | 희망 회차(지망 순) + **희망 지역 복수 선택** |
| 매칭 단위 | 회차 | **회차 × 지역** |
| 확정 기준 | 4~6명 하드 조건 만족 테이블 | 위 조건 + **한 회차·지역에서 테이블 2개 이상**일 때만 그 지역 테이블 생성 |
| 식당 | 회차 식당 풀에서 가격대 우선 | **테이블 지역의 식당** 중 가격대 우선, 회차 풀이 비면 지역 식당을 자동으로 풀에 넣는다 |
| 근거 | 그룹 점수 툴팁(관리자) | 관리자: 직업 캐릭터 그래프 + 쌍별 % + 항목별 근거 + 응답 원문 / 참가자: 장점 1~2줄 |
| 확정 안내 | 시스템 공지 1개 | **운영자 계정 메시지 2개**(장점 한 줄, 일시·지역·식당·지도 링크) + 취소 정책 시스템 공지 |

AI(LLM)는 쓰지 않는다. 모든 문구와 %는 매칭 엔진의 실제 점수에서 규칙으로 만든다.

## 1. 지역

- 지역은 고정 4곳: `홍대/합정`, `잠실`, `강남/신논현`, `을지로/종로` (PRD 가상 데이터·기존 FE `DINING_REGIONS`와 동일).
- BE: `domain/dining/entity/DiningRegion` enum — `HONGDAE("홍대/합정")`, `JAMSIL("잠실")`, `GANGNAM("강남/신논현")`, `EULJIRO("을지로/종로")`. **API·DB에는 라벨 문자열을 그대로 쓴다**(기존 `venues.region`, `dining_tables.region` 이 텍스트라서). 들어오는 값은 `DiningRegion.fromLabel` 로 검증하고 모르면 `INVALID_REGION`(400).
- `GET /api/dining/regions` → `["홍대/합정","잠실","강남/신논현","을지로/종로"]` (enum 선언 순서). FE는 이 응답을 쓴다(하드코딩 상수 제거).

## 2. 데이터 변경

### V28 (KAN-430)
```sql
CREATE TABLE application_candidate_regions (
  application_id UUID        NOT NULL REFERENCES applications(id),
  region         VARCHAR(50) NOT NULL,
  created_at     TIMESTAMP   NOT NULL DEFAULT now(),
  PRIMARY KEY (application_id, region)
);
-- 백필: 진행 중인 식탁 신청(희망 회차가 있는 신청)은 희망 회차 장소명이 4개 지역 중 하나면 그 지역들,
--       하나도 안 맞으면 4개 지역 전부.
```
### V29 (KAN-431)
```sql
ALTER TABLE matching_rule_settings ADD COLUMN min_tables_per_region INTEGER NOT NULL DEFAULT 2;
-- 식당 20곳 시드(지역별 5곳, 7장 목록). name+region 이 같은 미삭제 행이 있으면 건너뛴다(멱등).
```

## 3. 회차·신청 (KAN-430 [BE], KAN-433 [FE])

- 관리자 회차 생성·수정: **우연한 식탁(RANDOM_TABLE) 회차는 `locationId` 선택**(null 허용). 다른 종류는 지금처럼 필수. `GatheringSessionRequest.locationId` 의 `@NotNull` 을 서비스 검증으로 옮긴다.
- 신청 `POST /api/applications` 요청에 `candidateRegions: string[]` 추가.
  - RANDOM_TABLE: 1개 이상 필수, 중복 제거, 4개 라벨만 허용. 없으면 `REGION_REQUIRED`(400).
  - 다른 종류: 무시.
- 응답에 지역 노출:
  - 내 식탁 신청 목록 `DiningApplicationItem.candidateRegions: string[]`
  - 관리자 신청자 `DiningApplicant.candidateRegions: string[]`
  - 회차 표시용 `region` 필드(`DiningSessionInfo.region` 등)는 **테이블이 있으면 테이블 지역, 없으면 null**. 회차 장소명을 지역으로 쓰던 곳(`DiningTable.getDisplayRegion`, 추천·히스토리·해결 응답)은 테이블 지역 → 신청 희망 지역 순으로 바꾼다.
- 대체 회차 이동(해결 선택 TRANSFER)·재신청은 희망 지역을 그대로 유지한다.

## 4. 회차×지역 매칭 (KAN-431 [BE])

`DiningMatchService.runMatch` 의 후보 선정(지금과 동일) 뒤를 바꾼다.

1. 후보를 희망 지역으로 묶는다. 한 사람이 여러 지역 후보군에 동시에 들어간다.
2. **지역 처리 순서**: 후보 수 많은 지역 → (동률) 그 지역만 고른 사람 수 많은 지역 → `DiningRegion` 선언 순서. 결정적이어야 한다(무작위 없음).
3. 지역마다 `남은 후보 ∩ 이 지역 희망자` 로 기존 `MatchingEngine.match` 실행.
   - 결과 테이블이 `minTablesPerRegion`(기본 2) 이상이면 **그 지역 테이블을 채택**하고, 테이블에 앉은 사람을 남은 후보에서 뺀다. 엔진 미배정자는 남은 후보로 돌아간다.
   - 미만이면 **그 지역 결과를 버린다**(아무도 빼지 않는다). → PRD 7.4 ①② "같은 날짜·다른 희망 지역" 이 자연스럽게 처리된다.
   - ponytail: 지역 순서 그리디라 여러 지역을 고른 사람이 앞 지역에 먼저 흡수된다. 지역 간 최적 분배가 필요해지면 "버려진 지역에 대해 앞 지역의 다지역 희망자를 옮겨 보는" 2차 패스를 추가한다.
4. 채택된 테이블은 `DiningTable.region = 지역 라벨` 로 저장한다(지금은 null).
5. 끝까지 남은 사람은 지금의 `settleUnassigned` 그대로: 아직 실행 안 된 다른 희망 회차가 있으면 REALLOCATING(다음 회차 매칭 때 같은 희망 지역으로 다시 후보 — PRD 7.4 ③④), 없으면 ALTERNATIVE_OFFERED(대체 회차·보관·환불, 7.4 ⑤⑥). 미배정 사유에 새 값 `REGION_TOO_SMALL`(지역 테이블 2개 미만) 추가.
6. `MatchRun.unassignedReasons` 와 관리자 테이블 목록 응답에 지역별 결과(`regionResults: [{region, candidateCount, tableCount, adopted}]`)를 남긴다.
7. 관리자 수동 조작(이동·분리·병합)은 **같은 지역 테이블끼리만** 허용(`REGION_MISMATCH` 409). 2테이블 기준은 자동 매칭에만 적용한다.
8. 확정 직전 재검증(MAT-07)에 "멤버 전원이 테이블 지역을 희망" 조건을 추가한다.
9. 관리자 매칭 규칙 화면에 `minTablesPerRegion`(1~5) 노출 — `MatchingRuleRequest/Response`.

점수 미달로 다음 지망 날짜로 미루는 규칙은 넣지 않는다(KAN-420 에서 점수 미달 미배정을 없앴다). 필요하면 별도 티켓.

## 5. 식당 자동 배정 (KAN-431 [BE])

`SessionVenueService.useFirstAvailableVenue(sessionId, preferredPriceRanges)` 에 `region` 인자를 추가한다.

1. 회차 식당 풀 중 `venue.region == 테이블 지역` 이고 여유 있는 식당만 본다(가격대 교집합 우선 — 지금 규칙 유지).
2. 그런 풀 행이 하나도 없으면 **그 지역의 활성 식당 전부를 회차 풀에 `capacity_tables = 1` 로 자동 추가**(이미 있는 행은 건드리지 않음)한 뒤 1을 다시 한다. 운영자가 회차 풀을 미리 짜 두면 그게 우선이고, 안 짜 두면 지역 식당이 자동으로 쓰인다.
3. 그래도 없으면 지금처럼 VENUE 예외 케이스.
4. 관리자 식당(장소 관리) 등록·수정의 `region` 은 4개 라벨만 허용(`INVALID_REGION`). 기존 자유 텍스트 식당은 그대로 두되 자동 배정 대상에서 빠진다.

## 6. 매칭 근거 (KAN-432 [BE], KAN-434 [FE])

### 6.1 엔진
`MatchingEngine` 에 공개 메서드 추가: `Explanation explain(List<Applicant> members, Rules rules, Relations relations)`
```java
record PairScore(UUID a, UUID b, double score, Map<String, Double> components) {}  // a,b = applicationId, components 키: gender, mbti, interests, wantedStyle, custom (값 없는 항목은 키 없음)
record Explanation(List<PairScore> pairs, Score group, Map<String, Double> criteriaAvg)  // criteriaAvg = 항목별 쌍 평균(값 있는 쌍만)
```
계산은 기존 `Run.pairScore`/`groupScore` 를 그대로 쓴다(공식 변경 없음). **저장하지 않고 조회 때 현재 멤버로 계산**한다 — 이동·분리·병합 뒤에도 항상 맞는다.

### 6.2 장점 문구 (참가자·채팅 공용, 규칙)
`criteriaAvg` 에서 값이 0.5 이상인 항목을 높은 순으로 최대 2개. % = 반올림(값×100).

| 항목 | 문구 |
|---|---|
| wantedStyle | 대화 스타일 {p}% 일치하는 테이블이에요! |
| interests | 모두가 함께 좋아하는 관심사가 있으면 `'{관심사}'를 함께 좋아하는 테이블이에요!`, 없으면 `관심사가 {p}% 겹치는 테이블이에요!` |
| mbti | MBTI 궁합 {p}% 테이블이에요! |
| gender | 성비가 고르게 맞춰진 테이블이에요! (% 없음) |
| custom | 취향이 {p}% 맞는 테이블이에요! |

0.5 이상이 하나도 없으면 `모두 처음 만나는 사람들로 꾸린 테이블이에요!`(이전 만남 없음일 때) 또는 `새로운 사람들과 만나는 테이블이에요!`.
참가자에게는 **쌍별 점수·그룹 점수·다른 사람 응답을 주지 않는다**(MAT-12).

### 6.3 API
- 관리자 `GET /api/admin/dining/tables/{id}/explanation`
  ```json
  {
    "tableId": "…", "region": "강남/신논현", "groupScore": 0.72,
    "scoreDetail": {"pairAvg": 0.7, "pairMin": 0.5, "penalties": {}},
    "criteriaAvg": {"wantedStyle": 0.8, "mbti": 0.6},
    "strengths": ["대화 스타일 80% 일치하는 테이블이에요!", "MBTI 궁합 60% 테이블이에요!"],
    "members": [{"applicationId": "…", "userId": "…", "nickname": "…", "characterUrl": "…", "job": "개발자",
                 "birthYear": 1995, "gender": "FEMALE", "mbti": "ENFP",
                 "answers": [{"label": "관심사", "value": "전시, 와인"}]}],
    "pairs": [{"a": "<applicationId>", "b": "<applicationId>", "score": 0.83,
               "components": {"gender": 1.0, "mbti": 0.5, "interests": 0.4, "wantedStyle": 1.0}}]
  }
  ```
  `answers` 는 신청 `formSnapshot` 의 질문 라벨·답(시스템 키 이름·전화·이메일 제외).
- 참가자 `GET /api/dining/tables/{id}` 응답에 추가: `strengths: string[]`, `members[].characterUrl`.
- `characterUrl` 은 `CharacterAssetResolver.resolve(user.job)` (user 도메인 서비스 경유).

### 6.4 확정 채팅 메시지 (KAN-432)
`DiningConfirmService` 의 채팅방 생성 직후, **시스템 관리자 계정(`dining.system-admin-id`) 이름으로 일반 메시지 2개**(`ChatService.sendMessage`)를 보낸다.
1. `strengths[0]` (예: `대화 스타일 80% 일치하는 테이블이에요!`)
2. `{M월 d일 E요일 오전/오후 h시[ m분]} {지역} {식당명}에서 만나요! {mapUrl}` — 예: `10월 22일 목요일 오후 7시 강남/신논현 브리비트에서 만나요! https://naver.me/…`. 식당이 없으면 `… {지역}에서 만나요! 식당은 정해지면 이 방에서 알려드릴게요.`, mapUrl 이 없으면 링크 생략.

이어서 기존 시스템 공지는 취소 정책 한 줄만 남긴다(INF-05). 리마인드(KAN-349) 공지는 그대로. 같은 테이블에 두 번 보내지 않는다(채팅방이 이미 있으면 건너뛰는 지금의 멱등 그대로).

## 7. 식당 시드 (KAN-431, V29)

지역별 5곳, 총 20곳. 소개팅 장소로 알려진 4~6인 착석 가능 식당. 값: name, address(도로명), map_url(네이버 지도 검색 링크), price_range(PRICE_RANGE 선택지 3개 중 하나), region(라벨). 목록은 V29 파일에 둔다.

## 8. FE

### KAN-433 [FE] 지역 선택·표시
- 신청 1단계: 희망 회차 목록 위에 "희망 지역 (복수 선택)" 칩 4개(`GET /api/dining/regions`). 0개면 다음 버튼 비활성. draft(sessionStorage)에 `regions` 추가. 3단계 요약에 희망 지역 한 줄. 제출 payload `candidateRegions`.
- 식탁 회차 목록·상세·카드에서 `location` 표시는 location 이 있을 때만(이미 대부분 조건부) — 식탁 회차는 "서울 4개 지역" 안내 한 줄로 대체.
- 관리자 회차 모달: 식탁이면 지역/장소 선택 제거(`locationId` 미전송), `DINING_REGIONS` 상수 삭제.
- 관리자 회차 콘솔: 헤더 '지역' 행 제거, 신청자 표에 '희망 지역' 열, 테이블 카드에 지역 칩, 마지막 실행 요약에 `regionResults`.
- 관리자 식당(장소 관리) 폼: 지역을 4개 select 로.
- 관리자 매칭 규칙: `minTablesPerRegion` 입력.
- 마이페이지 신청 카드: 희망 지역 표시.

### KAN-434 [FE] 매칭 근거
- 관리자 테이블 카드(`DiningTableBoard`)에 "매칭 근거" 버튼 → 모달.
  - SVG 그래프: 멤버를 원형 배치, 노드 = 직업 캐릭터(`characterUrl`, 실패 시 `/assets/job/DEFAULT_SUIT.png`) + 닉네임. 모든 쌍을 선으로 잇고 선 중앙에 %; 선 굵기·진하기는 점수 비례. 선(또는 % 라벨)을 누르면 항목별 근거 표시. 키보드로 쌍 목록 탐색 가능(그래프 아래 쌍 목록 표, 높은 순).
  - 상단: 그룹 점수 %, 장점 문구, 페널티. 하단: 멤버별 응답 원문 펼치기.
  - 차트 라이브러리 추가 없이 SVG 직접.
- 참가자 확정 화면(`DiningTableView`): 요약 카드 아래 "이 테이블은요" 카드에 `strengths` 1~2줄. 멤버 목록에 캐릭터 이미지. 점수·선은 없음.

## 9. 티켓·순서

| 티켓 | 내용 | Flyway | 선행 |
|---|---|---|---|
| KAN-430 [BE] | 지역 enum·조회 API, 신청 희망 지역, 식탁 회차 장소 선택화, 지역 표시 소스 변경 | V28 | — |
| KAN-431 [BE] | 회차×지역 매칭·2테이블 기준·지역 재검증·수동 조작 제한, 지역 식당 자동 배정, 식당 20곳 시드, 규칙 설정 | V29 | A |
| KAN-432 [BE] | 엔진 explain, 관리자 근거 API, 참가자 장점·캐릭터, 확정 채팅 메시지 2개 | — | (B 와 `DiningConfirmService` 겹침 → B 다음 머지) |
| KAN-433 [FE] | 8장 KAN-433 | — | 계약은 이 문서 |
| KAN-434 [FE] | 8장 KAN-434 | — | 계약은 이 문서 |

병렬: A·C·D·E 동시 시작, B 는 A 머지 뒤. 머지 순서 A → B → C, FE 는 BE 머지 후 계약 대조(`types.ts`) 뒤 머지.

## 10. 테스트

- A: `ApplicationServiceTest`(희망 지역 필수·검증·저장), `AdminGatheringServiceTest`(식탁 회차 locationId null 허용), 마이그레이션 백필.
- B: `DiningMatchServiceTest` — (1) 한 지역 8명 이상·다른 지역 4명 → 큰 지역만 채택, 작은 지역 인원은 REALLOCATING/REGION_TOO_SMALL, (2) 두 지역 희망자가 다른 지역으로 흘러 채택되는 경우, (3) 테이블 region 저장, (4) 결정성. `SessionVenueServiceTest` — 지역 필터·자동 풀 추가·가격대 우선. `DiningTableServiceTest` — 지역 다른 테이블 간 이동 거부.
- C: `MatchingEngineTest`(explain 이 match 점수와 같은 값), 장점 문구 규칙 단위 테스트, `DiningConfirmPipelineTest`(메시지 2개·문구·식당 없음 분기), 참가자 API 에 쌍별 점수 없음.
