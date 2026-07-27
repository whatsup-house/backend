---
name: backend
description: 이 프로젝트 Spring Boot 구현 규칙 - Controller, Service, JPA 패턴, 네이밍 컨벤션
---

# Backend 구현 규칙

## Controller

- HTTP 요청/응답 처리만 담당. 비즈니스 로직, 엔티티 조작, 복잡한 조건 분기 금지.
- Service를 호출해 유스케이스 흐름을 위임한다.
- 인증/인가 판단을 Controller에서 처리하지 않는다.

## 요청 처리

- 요청값은 DTO로 받는다. 엔티티를 요청 객체로 사용하지 않는다.
- DTO는 일반 클래스 사용 (record 지양).
- Request DTO 필드: Bean Validation 어노테이션 + `@Schema(example = "...")` 필수.
  - 필수 문자열: `@NotBlank` / 필수 객체·ID·숫자: `@NotNull`
  - 문자열 길이: `@Size(min, max)` / 숫자 범위: `@Min`, `@Max`
  - 이메일: `@Email` / 정규식: `@Pattern(regexp)`
- Controller `@RequestBody`에 `@Valid` 필수.
- 검증 오류는 공통 예외 처리 흐름이 처리. Controller에서 직접 핸들링 금지.

## 응답 처리

- 모든 API 응답은 `ApiResponse<T>` 래핑. 엔티티를 직접 반환하지 않는다.
- Response DTO의 `from(entity)` 패턴 허용.

## 예외 처리

- `CustomException(ErrorCode)` 사용. Controller에서 try-catch 금지.
- `RuntimeException`, `IllegalArgumentException` 직접 던지기 금지.
- `ErrorCode`에 항목이 없으면 새로 추가하고 사용한다.
- 모든 비즈니스 예외는 즉시 명시적으로 던진다. null 반환 금지.

## API 경로

- 인증 API: `/api/auth/**` → 공개
- 관리자 API: `/api/admin/**` → ROLE_ADMIN 필요
- 유저 API: `/api/**` → JWT 인증 필요
- 공개 조회 API: `GET /api/gatherings/**` → 인증 없이 접근 가능
- 경로는 명사형 리소스 중심.

## 인증 주체

- `@AuthenticationPrincipal UserPrincipal` 사용.

## Swagger / OpenAPI

- 클래스: `@Tag(name = "도메인명(한글)", description = "API 그룹 설명")`
- 메서드: `@Operation(summary = "짧은 제목", description = "상세 설명")`
- 파라미터: `@Parameter(description = "설명", example = "예시값")`
- Response DTO 필드: `@Schema(description = "설명", example = "예시값")`

## 코딩 컨벤션

- Lombok: `@Getter`, `@Builder`, `@RequiredArgsConstructor`
- `@Setter` 지양.
- Service 기본 `@Transactional`, 조회 전용은 `readOnly = true`.

---

## JPA 규칙

### 엔티티 설계

- 엔티티는 상태와 행위를 함께 가지는 도메인 객체. setter 지양, 상태 변경 메서드 사용.
- JPA 기본 생성자는 `protected`. 핵심 값은 생성자 또는 정적 팩토리 메서드로 받는다.
- `BaseEntity` 상속 (soft delete: `deletedAt` 필드 활용).

### DTO와 엔티티 분리

- 엔티티를 API 요청/응답으로 직접 사용하지 않는다.
- Response DTO: `from(entity)` 패턴 허용.
- Request DTO: 단순 생성만 `toEntity()` 허용. 복잡한 조합은 Service에서.
- 엔티티에 `toDto()`, `fromDto()` 메서드를 두지 않는다.

### 연관관계

- 기본 단방향 우선. 양방향은 꼭 필요할 때만. 연관관계 주인 명확히 관리.
- `@ManyToMany` 직접 사용 금지 → 연결 엔티티로 분리.

### Fetch 전략

- ToOne: 명시적 LAZY. EAGER 기본값에 의존 금지.
- ToMany: LAZY 유지.
- 필요한 데이터는 fetch join, DTO 조회, 별도 조회 전략으로 가져온다.

### 조회

- 목록 조회: 필요한 필드만 DTO 조회 우선.
- N+1 가능성 있으면 fetch join 또는 조회 구조 변경 먼저 검토.
- soft delete 데이터가 조회에 섞이지 않도록 (`deletedAt = null` 조건).

### 저장/수정

- 상태 변경은 엔티티 메서드로. 영속 엔티티 변경은 변경 감지 우선 활용.
- 생성과 수정 흐름을 구분.

### Cascade / orphanRemoval

- Aggregate 경계가 명확하고 함께 저장/삭제될 때만 사용.
- 무분별한 `CascadeType.ALL` 지양.

### 트랜잭션

- 트랜잭션 경계는 Service에서 관리. Controller에서 다루지 않는다.

### 예외 및 검증

- 자기 상태만으로 판단 가능한 검증은 엔티티에서.
- 다른 엔티티 조회나 외부 의존이 필요한 검증은 Service에서.

---

## 네이밍 컨벤션

### 클래스명

| 레이어 | 형식 | 예시 |
|--------|------|------|
| Entity | 도메인명 단수 | `Gathering` |
| Controller (유저) | `{도메인}Controller` | `GatheringController` |
| Controller (관리자) | `Admin{도메인}Controller` | `AdminGatheringController` |
| Service (유저) | `{도메인}Service` | `GatheringService` |
| Service (관리자) | `Admin{도메인}Service` | `AdminGatheringService` |
| Repository | `{도메인}Repository` | `GatheringRepository` |
| Request DTO | `{동작}{도메인}Request` | `GatheringCreateRequest` |
| Response DTO | `{도메인}{범위}Response` | `GatheringDetailResponse` |
| Enum | 대문자 단수형 | `GatheringStatus` |

- `Dto` 접미사 사용 금지.
- 관리자/유저 분리 시 패키지로 분리 (`admin/`, `client/`).

### 필드명

- Java: camelCase / DB: snake_case
- boolean: `is` 접두어 (`isAdmin`, `isAvailable`)
- 날짜/시간: `{의미}At` (`createdAt`, `deletedAt`), `{의미}Date`, `{의미}Time`
- 외래키만 저장: `{도메인}Id` (`locationId`)

### Enum 값

- UPPER_SNAKE_CASE

### Service 메서드명

| 동작 | 형식 | 예시 |
|------|------|------|
| 단건 조회 | `get{대상}` | `getProfile` |
| 목록 조회 | `list{복수대상}` | `listGatherings` |
| 내부 헬퍼 | `find{대상}` | `findActiveUser` |
| 생성 | `create{대상}` | `createGathering` |
| 등록 (회원) | `register{대상}` | `registerUser` |
| 수정 | `update{대상}` | `updateProfile` |
| 상태 변경 | `change{대상}Status` | `changeGatheringStatus` |
| 삭제 | `delete{대상}` | `deleteGathering` |
| 취소 | `cancel{대상}` | `cancelApplication` |
| 확인 | `is{조건}` | `isEmailAvailable` |

### Repository 메서드명

- Soft delete 조회: `AndDeletedAtIsNull` 조건 필수
- 단건: `findBy{조건}AndDeletedAtIsNull`
- 전체: `findAllBy{조건}AndDeletedAtIsNull`
- 존재: `existsBy{필드}`

### 패키지 구조

```
domain/{name}/
  controller/ (admin/, client/ 분리 가능)
  service/
  repository/
  entity/
  dto/request/, dto/response/
```

### 금지

- 약어 남발 (`usrSvc`, `gthrCtrl`)
- 숫자 접미 (`Gathering2`, `UserNew`)
- `Manager`, `Helper`, `Utils` 접미어를 Service 대신 사용
