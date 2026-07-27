# SonarQube 코드 리뷰 보고서

> 분석일: 2026-05-14  
> 프로젝트: `whatsup-house-backend`  
> Quality Gate: **PASSED**

---

## 전체 지표 요약

| 지표 | 값 | 등급 |
|------|-----|------|
| 코드 라인 수 | 2,156 | - |
| 커버리지 | 63.2% | - |
| 버그 | ~~1~~ 0 | ~~B~~ A |
| 취약점 | 0 | A |
| 코드 스멜 | 29 | A |
| 중복 코드 | 0.0% | - |
| 유지보수성 | - | A |

---

## CRITICAL — 즉시 수정 필요

### 1. ~~`@Transactional` 메서드를 `this`로 직접 호출 (버그)~~ ✅ 수정 완료
- **파일**: `ApplicationService.java`
- **규칙**: `java:S6809`
- **수정 내용**: `apply()` 로직을 `private applyInternal()`로 추출. `apply()`와 `applyAsGuest()` 각각 독립적으로 프록시를 통해 `@Transactional` 적용 후 `applyInternal()` 호출.
- **커밋**: `fix: ApplicationService @Transactional self-call 프록시 우회 버그 수정 (KAN-S6809)`

### 2. ~~문자열 리터럴 중복 — `SupabaseStorageService.java`~~ ✅ 수정 완료
- **파일**: `SupabaseStorageService.java`
- **규칙**: `java:S1192`
- `AUTHORIZATION = "Authorization"`, `BEARER_PREFIX = "Bearer "` 상수 추가 후 3개 호출부 모두 교체
- **커밋**: `refactor: SupabaseStorageService, AuthController 중복 문자열 상수화`

### 3. ~~문자열 리터럴 중복 — `AuthController.java`~~ ✅ 수정 완료
- **파일**: `AuthController.java`
- **규칙**: `java:S1192`
- `ACCESS_TOKEN = "accessToken"`, `REFRESH_TOKEN = "refreshToken"` 상수 추가 후 전체 호출부 교체
- **커밋**: 위와 동일

---

## MAJOR — 우선 처리 권장

### 4. ~~사용되지 않는 로컬 변수 할당~~ ✅ 수정 완료
- **파일**: `ApplicationRepositoryTest.java`
  `app1` 변수 할당 제거 → `saveApplication()` 반환값 무시로 수정 (158, 187번 라인)
- **파일**: `AdminApplicationServiceTest.java`
  미사용 `UUID id = applicationId;` 라인 제거 (347번 라인)
- **커밋**: `fix: 테스트 코드 미사용 로컬 변수 할당 제거 (S1854, S1481)`

---

## MINOR — 코드 품질 개선

### ~~테스트 Assertion 개선 (S5838)~~ ✅ 수정 완료
아래 파일에서 `.isEqualTo(0)` 대신 `.isZero()` 사용 권장.

| 파일 | 라인 |
|------|------|
| `AdminCarouselServiceTest.java` | 240, 409 |
| `CarouselSlideRepositoryTest.java` | 84 |
| `AuthServiceTest.java` | 137 |
| `AdminUserServiceTest.java` | 64, 78, 83 |
| `UserServiceTest.java` | 65, 90 |
| `AdminUserRepositoryTest.java` | 115 |
| `AdminGatheringServiceTest.java` | 108 |

- `CarouselSlideRepositoryTest.java:119` → `.contains()` 사용 권장 ✅ 수정 완료
- `AdminUserRepositoryTest.java:147` → List 비어있는지 먼저 확인 후 assertion (S5841) ✅ 수정 완료

### ~~불필요한 import 제거 (S1128)~~ ✅ 수정 완료
- `AdminUserRepositoryTest.java:8` — 같은 패키지 클래스 import 불필요 ✅ 수정 완료
- `AdminApplicationServiceTest.java:34` — 미사용 `ArgumentMatchers.any` import ✅ 수정 완료

### ~~미사용 로컬 변수 제거 (S1481)~~ ✅ 수정 완료
- `ApplicationRepositoryTest.java:158, 187` — 미사용 변수 `app1` ✅ 수정 완료
- `AdminApplicationServiceTest.java:347` — 미사용 변수 `id` ✅ 수정 완료

### 기타
- `SupabaseStorageService.java:44` — 하드코딩된 path delimiter `/` → 상수 사용 (S1075) ✅ 수정 완료
- `AdminApplicationServiceTest.java:353` — 빈 statement(세미콜론 단독 줄) 제거 (S1116) ✅ 수정 완료
- `AdminApplicationServiceTest.java:261` — Mockito `eq()` 불필요하게 사용 → 값 직접 전달 (S6068) ✅ 수정 완료
- `AdminCarouselService.java:116` — `Boolean` 박싱 타입 대신 원시 `boolean` 사용 (S5411) ✅ 수정 완료

---

## 우선순위별 Action Plan

| 우선순위 | 항목 | 담당 |
|---------|------|------|
| ~~P0~~ | ~~`ApplicationService.java:93` — 트랜잭션 우회 버그 수정~~ | ✅ 완료 |
| ~~P1~~ | ~~`SupabaseStorageService`, `AuthController` 문자열 상수화~~ | ✅ 완료 |
| ~~P2~~ | ~~테스트 코드 미사용 변수 및 빈 statement 정리~~ | ✅ 완료 (변수 제거) |
| ~~P3~~ | ~~Assertion 스타일 통일 (`isZero`, `contains`)~~ | ✅ 완료 |
| ~~P3~~ | ~~불필요한 import 제거~~ | ✅ 완료 |
