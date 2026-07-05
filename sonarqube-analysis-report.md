# SonarQube 정적 분석 보고서

> 분석 기준일: 2026-06-30  
> 마지막 SonarCloud 분석: 2026-05-11  
> 프로젝트: `whatsup-house_backend`

---

## 전체 품질 게이트

| 항목 | 상태 |
|------|------|
| **Quality Gate** | ❌ **FAILED** |
| 신규 코드 신뢰성 | ✅ A |
| 신규 코드 보안성 | ✅ A |
| 신규 코드 유지보수성 | ✅ A |
| **보안 핫스팟 검토율** | ❌ 0% (기준: 100%) |

---

## 전체 메트릭 요약

| 메트릭 | 값 | 등급 |
|--------|-----|------|
| 코드 라인 수 | 3,251 | - |
| 버그 | 0 | A |
| 취약점 | 0 | A |
| 보안 핫스팟 | 1 | 미검토 |
| 코드 스멜 | 23 | - |
| 기술 부채 비율 | 0.1% | A |
| 기술 부채 총량 | 135분 | - |
| 코드 커버리지 | 0.0% | ❌ |
| 중복 코드 비율 | 5.2% | - |
| 중복 블록 | 10개 / 10개 파일 | - |

---

## 보안 이슈

### 🔴 Security Hotspot (미검토 1건)

| 심각도 | 위치 | 규칙 |
|--------|------|------|
| HIGH | `global/config/SecurityConfig.java:48` | `java:S4502` |

**내용:** CSRF 보호 비활성화 (`csrf.disable()`)

**분석:**  
현재 프로젝트는 JWT + HttpOnly 쿠키 방식을 사용하므로 CSRF 비활성화가 의도적인 선택입니다. 단, SonarCloud는 이를 자동으로 알 수 없어 "검토 필요"로 표시합니다.

**조치 방법:**  
SonarCloud UI에서 해당 핫스팟을 `Safe` 또는 `Acknowledged`로 마크해야 Quality Gate를 통과합니다. 코드 변경 불필요.

```java
// SecurityConfig.java - 의도적 CSRF 비활성화 이유 명시
// JWT는 Authorization 헤더 또는 HttpOnly 쿠키로 전달되며,
// SameSite=Lax 쿠키 정책으로 CSRF 위협이 제한됩니다.
http.csrf(AbstractHttpConfigurer::disable)
```

---

## CRITICAL 이슈 (3건)

### 1. `@Transactional` 메서드를 `this`로 직접 호출 — `java:S6809`

- **위치:** `ApplicationService.java:93`
- **심각도:** CRITICAL / CODE_SMELL

**문제 코드:**
```java
// ApplicationService 내부
@Transactional
public ApplicationResponse apply(...) {
    return applyInternal(...);  // 프록시 우회 없음, OK
}

private ApplicationResponse applyInternal(...) {
    // 내부에서 this.someTransactionalMethod() 형태 호출 시
    // Spring AOP 프록시를 우회 → 트랜잭션이 적용되지 않음
}
```

**설계적 문제:**  
`applyInternal()`은 `private` 헬퍼 메서드이지만, `apply()`와 `applyAsGuest()` 두 `@Transactional` 퍼블릭 메서드가 이를 내부 호출합니다. 이 패턴 자체는 괜찮습니다. 다만 `applyInternal()` 안에서 `this.someOtherTransactionalMethod()`를 호출하면 트랜잭션이 합류(propagate)되지 않습니다.

**권장 조치:**
- 내부에서 트랜잭션 경계를 분리해야 한다면, 별도 서비스 빈(`ApplicationAnswerService` 등)으로 분리 후 주입
- 현재 구조를 유지한다면 `applyInternal()`이 단일 트랜잭션 내에서만 호출됨을 확인하고 Sonar 억제 주석 추가

```java
@SuppressWarnings("java:S6809")
private ApplicationResponse applyInternal(...) { ... }
```

---

### 2. 문자열 리터럴 상수화 미적용 — `java:S1192`

- **위치:** `AuthController.java:47-48`
- **심각도:** CRITICAL / CODE_SMELL

**현황 (이미 수정됨):**  
파일을 직접 확인한 결과, `AuthController`에는 이미 상수가 정의되어 있습니다:

```java
private static final String ACCESS_TOKEN = "accessToken";
private static final String REFRESH_TOKEN = "refreshToken";
```

SonarCloud 분석 시점(2026-05-11)과 현재 코드 사이에 수정이 완료된 것으로 보입니다. **재분석 시 자동 해소 예정.**

---

## MAJOR 이슈 (5건)

### 3. 사용되지 않는 로컬 변수 — `java:S1854`

| 위치 | 변수 |
|------|------|
| `AdminApplicationServiceTest.java:283` | `id` |
| `ApplicationRepositoryTest.java:158` | `app1` |
| `ApplicationRepositoryTest.java:187` | `app1` |

**예시 패턴:**
```java
// 수정 전
Long id = service.create(request);  // 반환값을 저장하나 사용하지 않음

// 수정 후 (1) - 변수 제거
service.create(request);

// 수정 후 (2) - 실제 검증에 활용
Long id = service.create(request);
assertThat(id).isNotNull();
```

---

### 4. `@Override` 어노테이션 누락 — `java:S1161`

- **위치:** `CarouselSlide.java:77`

```java
// 수정 전
public String toString() { ... }

// 수정 후
@Override
public String toString() { ... }
```

또한 MINOR 이슈(`java:S2177`)로 "부모 메서드를 단순 상속하면 되는 불필요한 오버라이드"도 함께 감지되었습니다. 메서드 내용이 단순 위임이라면 삭제가 더 깔끔합니다.

---

### 5. 생성자 파라미터 과다 (9개) — `java:S107`

- **위치:** `User.java:73`, `Gathering.java:76`

**설계적 문제:**  
`@Builder`와 함께 사용 중이라 호출부에서 파라미터 순서를 강제하진 않지만, 생성자 파라미터가 9개라는 건 해당 엔티티가 너무 많은 책임을 가지거나 값 객체로 분리할 후보 필드가 있음을 시사합니다.

**User 엔티티 분리 제안:**
```
User
├── 인증 정보: email, password (→ Credentials VO 후보)
├── 프로필: name, gender, age, birthDate, nickname (→ UserProfile VO 후보)
└── 연락처: phone, instagramId (→ Contact VO 후보)
```

**Gathering 엔티티 분리 제안:**
```
Gathering
├── 스케줄: eventDate, startTime, endTime (→ GatheringSchedule VO 후보)
├── 장소/인원: location, maxAttendees (→ 기존 Location 활용)
└── 컨텐츠: title, description, thumbnailUrl, tags, howToRun
```

단기적으로는 Builder 패턴을 유지하면서 `@Builder`의 `builderClassName`/`toBuilder` 활용, 장기적으로는 Embedded VO 분리를 권장합니다.

---

## MINOR 이슈 (14건)

### 6. 테스트 assertion 관용 표현 미사용

SonarQube가 감지한 테스트 코드 패턴 문제들:

| 위치 | 현재 코드 | 권장 코드 |
|------|-----------|-----------|
| 여러 테스트 파일 | `assertThat(x).isEqualTo(0)` | `assertThat(x).isZero()` |
| `CarouselSlideRepositoryTest.java:119` | `assertThat(list).contains(x)` 관련 | `assertThat(actual).contains(expected)` |

### 7. 정규식 개선 — `java:S5867`

- **위치:** `ApplicationRequest.java:17`

```java
// 수정 전
@Pattern(regexp = "^[0-9]{10,11}$")

// 수정 후
@Pattern(regexp = "^\\d{10,11}$")
```

### 8. 불필요한 Boolean 박싱 — `AdminCarouselService.java:107`

```java
// 수정 전
if (Boolean.TRUE.equals(someCondition)) { ... }

// 수정 후 (필드가 primitive boolean이라면)
if (someCondition) { ... }
```

---

## 중복 코드 (5.2%, 210라인)

### 주요 중복 파일

| 파일 | 중복 비율 |
|------|----------|
| `gathering/admin/dto/request/GatheringCreateRequest.java` | 65.3% |
| `gathering/admin/dto/request/GatheringUpdateRequest.java` | 65.3% |
| `carousel/admin/dto/request/CarouselSlideCreateRequest.java` | 59.0% |
| `carousel/admin/dto/request/CarouselSlideUpdateRequest.java` | 59.0% |
| `location/admin/dto/request/LocationUpdateRequest.java` | 47.2% |
| `location/admin/dto/request/LocationCreateRequest.java` | 36.2% |
| `carousel/admin/dto/response/AdminCarouselSlideResponse.java` | 33.3% |
| `carousel/common/dto/response/CarouselSlideResponse.java` | 38.3% |

### 원인 및 해결 방향

Create/Update DTO 쌍이 동일 필드를 별도 클래스로 중복 선언하는 패턴입니다.

**방법 1: 공통 베이스 클래스 추출**
```java
// GatheringBaseRequest.java (공통 필드)
public abstract class GatheringBaseRequest {
    @NotBlank private String title;
    private String description;
    private LocalDate eventDate;
    // ...
}

public class GatheringCreateRequest extends GatheringBaseRequest { ... }
public class GatheringUpdateRequest extends GatheringBaseRequest { ... }
```

**방법 2: 단일 Upsert DTO + nullable 처리**
```java
// id 여부로 create/update 구분
public class GatheringUpsertRequest {
    private UUID id;  // null이면 create
    @NotBlank private String title;
    // ...
}
```

Response DTO의 경우 (`AdminCarouselSlideResponse` vs `CarouselSlideResponse`) 관리자/공개 응답이 거의 동일하다면 단일 DTO + 필드 노출 제어(Jackson View 등)를 고려할 수 있습니다.

---

## 설계적 문제 (SonarQube 외 코드 리뷰)

### 9. ApplicationService — God Class 징후

`ApplicationService`의 의존성 목록:

```
ApplicationRepository, GatheringRepository, UserRepository,
FormRepository, FormQuestionRepository, ApplicationAnswerRepository,
ApplicationEventPublisher, FormProvisionService, TicketService,
TicketTransactionRepository, ParticipantService, AuthService,
ApplicationLookupTokenService
```

**13개 의존성**은 단일 책임 원칙(SRP) 위반 징후입니다.

**분리 제안:**
```
ApplicationService (신청 생성 핵심 로직)
├── ApplicationQueryService (조회: checkApplication, getMyApplications)
├── ApplicationAnswerService (답변 저장/로드)
└── ApplicationCancellationService (취소 로직)
```

현재 `applyInternal()` 메서드 하나가 230줄에 달하며, 폼 검증 → 참가자 생성 → 좌석 확인 → 이용권 처리 → 이벤트 발행을 모두 담당합니다.

---

### 10. AuthController — 등록/게스트 이메일 인증 엔드포인트 중복

`/register-email-verification/*`와 `/guest-email-verification/*`가 **동일한 서비스 메서드**를 호출합니다:

```java
// 두 메서드가 완전히 동일한 서비스 호출
@PostMapping("/guest-email-verification/request")
public ResponseEntity<?> requestGuestEmailVerification(...) {
    return ResponseEntity.ok(ApiResult.success(authService.requestGuestEmailVerification(request)));
}

@PostMapping("/register-email-verification/request")
public ResponseEntity<?> requestRegisterEmailVerification(...) {
    return ResponseEntity.ok(ApiResult.success(authService.requestGuestEmailVerification(request)));  // 동일!
}
```

엔드포인트를 분리한 이유(프론트 UX 구분 등)가 있다면 유지할 수 있으나, 내부 서비스 로직도 동일하다면 하나의 범용 엔드포인트로 통합을 검토하세요.

---

### 11. SecurityConfig — CORS `allowedOrigins` 하드코딩

```java
config.setAllowedOrigins(List.of(
    "http://localhost:3000",
    "https://whatsup-house.vercel.app",
    "https://whatsup.house",
    "https://www.whatsup.house"
));
```

프로덕션 배포 환경이 변경될 때마다 코드 수정이 필요합니다. `@Value` 또는 `@ConfigurationProperties`로 외부화를 권장합니다:

```java
@Value("${cors.allowed-origins}")
private List<String> allowedOrigins;
```

---

## 우선순위별 액션 플랜

### 즉시 조치 (Quality Gate 통과용)
- [ ] SonarCloud UI에서 SecurityConfig CSRF 핫스팟을 `Safe`로 마크

### 단기 (다음 스프린트)
- [ ] 테스트 코드 미사용 변수 제거 (`id`, `app1` × 3건)
- [ ] `CarouselSlide.java` `@Override` 추가 또는 불필요 메서드 제거
- [ ] `ApplicationRequest.java` 정규식 `\d` 사용

### 중기 (리팩토링 스프린트)
- [ ] `GatheringCreateRequest` / `GatheringUpdateRequest` 공통 베이스 추출
- [ ] `CarouselSlideCreateRequest` / `CarouselSlideUpdateRequest` 동일 처리
- [ ] `ApplicationService` 책임 분리 (Query / Answer / Cancellation)

### 장기 (아키텍처 개선)
- [ ] `User`, `Gathering` 엔티티 Value Object 분리로 생성자 파라미터 축소
- [ ] CORS 설정 외부화 (`application.yml`)
- [ ] 테스트 커버리지 개선 (현재 0.0% — SonarCloud 미측정 가능성 있음)
