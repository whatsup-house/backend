# API Rules

이 문서는 API 설계, 컨트롤러 구현, 요청/응답 처리 시 따라야 할 규칙을 정의한다.

---

## 1. Controller 역할

- HTTP 요청/응답 처리만 담당한다. 비즈니스 로직, 엔티티 직접 조작, 복잡한 조건 분기 금지.
- Service를 호출해 유스케이스 흐름을 위임한다.
- 인증/인가 판단을 Controller에서 직접 처리하지 않는다.

---

## 2. 요청 처리

- 요청값은 DTO로 받는다. 엔티티를 요청 객체로 사용하지 않는다.
- `@RequestBody`, `@PathVariable`, `@RequestParam`의 역할을 명확히 구분한다.
- DTO는 일반 클래스 사용 (record 지양).
- 요청 검증: DTO에서 가능한 범위까지 처리, 복잡한 검증은 Service에서.
- Request DTO 필드에 `@Schema(example = "...")` 필수.

### 입력 검증

Request DTO 필드에 Bean Validation 어노테이션 필수:

| 상황 | 어노테이션 |
|------|-----------|
| 필수 문자열 | `@NotBlank` |
| 필수 객체/ID/숫자 | `@NotNull` |
| 문자열 길이 | `@Size(min, max)` |
| 숫자 범위 | `@Min`, `@Max` |
| 이메일 | `@Email` |
| 정규식 | `@Pattern(regexp)` |

- Controller `@RequestBody`에 `@Valid` 필수.
- 검증 오류는 공통 예외 처리 흐름이 처리. Controller에서 직접 핸들링 금지.

---

## 3. 응답 처리

- 모든 API 응답은 `ApiResponse<T>` 래핑. 엔티티를 직접 반환하지 않는다.
- Response DTO의 `from(entity)` 패턴 허용.

```java
return ResponseEntity.ok(ApiResponse.success(responseDto));
```

---

## 4. 예외 처리

- `CustomException(ErrorCode)` 사용. Controller에서 try-catch 금지.
- 직접 `RuntimeException`, `IllegalArgumentException`을 던지지 않는다.
- `ErrorCode`에 적절한 항목이 없으면 새로 추가하고 사용한다.
- 모든 비즈니스 예외는 즉시 명시적으로 던진다. 조건을 흘려보내거나 null 반환 금지.

### 예외 패턴

```java
// 조회 실패
User user = userRepository.findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

// 중복 확인
if (userRepository.existsByEmail(email)) {
    throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
}

// 상태/권한 검증
if (gathering.getStatus() != GatheringStatus.OPEN) {
    throw new CustomException(ErrorCode.GATHERING_NOT_RECRUITING);
}
```

---

## 5. API 경로

- 인증 API: `/api/auth/**` → 공개
- 관리자 API: `/api/admin/**` → ROLE_ADMIN 필요
- 유저 API: `/api/**` → JWT 인증 필요
- 공개 조회 API: `GET /api/gatherings/**` → 인증 없이 접근 가능
- 경로는 명사형 리소스 중심 (`/api/gatherings/{id}` ○, `/api/getGathering` ✗)

---

## 6. 인증 주체

- `@AuthenticationPrincipal UserPrincipal` 사용.

---

## 7. Swagger / OpenAPI

- Swagger 문서와 실제 구현을 일치시킨다.
- 클래스: `@Tag(name = "도메인명(한글)", description = "API 그룹 설명")`
- 메서드: `@Operation(summary = "짧은 제목", description = "상세 설명")`
- 파라미터: `@Parameter(description = "설명", example = "예시값")`
- Response DTO 필드: `@Schema(description = "설명", example = "예시값")`

---

## 8. 코딩 컨벤션

- Lombok: `@Getter`, `@Builder`, `@RequiredArgsConstructor`
- `@Setter` 지양
- Service 기본 `@Transactional`, 조회 전용은 `readOnly = true` 고려
