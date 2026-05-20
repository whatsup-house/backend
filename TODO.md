# 배포 전 리팩토링 TODO

---

## 1. 디렉토리 구조 정리

### 1-1. `image` 도메인 위치 불일치 ✅
- `ImageController` → `global/storage/controller/`로 이동 완료

### 1-2. `carousel` 홈 API 컨트롤러 위치 불일치 ✅
- `CarouselController`, `HomeGatheringController` → `domain/home/controller/`로 이동 완료

### 1-3. `global/storage` DTO 위치 ✅
- 1-1 이동으로 컨트롤러와 DTO 모두 `global/storage/` 하위로 통일 완료

---

## 2. 네이밍 컨벤션 정리

### 2-1. boolean 필드 JPA 컬럼 네이밍 혼용 — 현재 방식 유지 (팀 논의 후 결정)
- `CarouselSlide.isActive`, `Gathering.isCurated`, `User.isAdmin` — 현재 방식 고수 결정

### 2-2. 메서드 네이밍 — Admin/Client 서비스 간 동사 혼용 ✅
- `GatheringService.getGatherings()` → `listGatherings()`
- `GatheringController.getGatherings()` → `listGatherings()`

### 2-3. Request/Response DTO 네이밍 — Admin prefix 불일치 ✅ (완료 확인)
- `ApplicationStatusRequest`, `ApplicationDeleteResponse`, `ApplicationStatusResponse`, `UserListResponse`, `UserListResponse` 모두 이미 적용됨

### 2-4. Repository 조회 결과 타입 — `*Row` suffix ✅ (완료 확인)
- `UserApplicationStatsProjection`으로 이미 적용됨

---

## 3. 역할 분리 (레이어 경계)

### 3-1. `UserService.findActiveUser()` — public 노출 범위 ✅
- `UserService.findActiveUser()` → `private`으로 변경

### 3-2. `AdminCarouselService.reorderSlides()` — 엔티티 update 메서드 오용 ✅
- `CarouselSlide.updateSortOrder(int sortOrder)` 메서드 추가
- `reorderSlides()` 내 `slide.update(...)` → `slide.updateSortOrder(i)` 로 교체

### 3-3. `GatheringService` — 필터 조건 분기 중복 — 현재 구조 유지
- 클라이언트/어드민 필터 조건이 실질적으로 달라 분리가 맞는 구조로 결론

---

## 4. 코드 리뷰 보고서 이슈 (`docs/review_report.md`)

> 진행 기준: 우선순위 8개 항목 순서대로 처리

- [x] 1번 — `SecurityConfig.anyRequest().permitAll()` → `denyAll()` (커밋: `fix: SecurityConfig anyRequest().permitAll() → denyAll() 보안 정책 강화`)
- [x] 2번 — N+1 쿼리 (`ApplicationRepositoryCustomImpl` fetch join, `ApplicationRepository` / `GatheringRepository` `@EntityGraph`) (커밋: `fix: N+1 쿼리 해결 — fetch join 및 @EntityGraph 추가`)
- [x] 3번 — `User.updateProfile()` null 덮어쓰기 — `ProfileUpdateRequest` 필수 필드에 `@NotNull` 추가
- [x] 4번 — soft delete 조회 일관성 — `AdminApplicationService.deleteApplication()`의 `findById()` → `findByIdAndDeletedAtIsNull()` 교체
- [x] 5번 — 토큰 만료/위변조 미구분 — `JwtTokenProvider.validateToken()`에서 `ExpiredJwtException` 별도 catch, `TOKEN_EXPIRED` 에러코드 실제 사용
- [x] 6번 — 인증 실패 응답 포맷 불일치 — `CustomAuthEntryPoint` 생성, `ApiResult.fail(...)` JSON으로 응답
- [x] 7번 — 토큰 응답 바디 노출 — `LoginResponse` 토큰 필드에 `@JsonIgnore` 추가, 쿠키로만 전달
- [ ] 8번 — carousel 경로 충돌 — 프론트 협의 완료로 스킵

---

## 5. SonarQube 정적 분석 (이후 단계)

- [x] SonarQube 연동 설정 (`build.gradle` 플러그인 — sonarqube 6.0.1.5171, jacoco)
- [x] `docker-compose.yml`에 SonarQube + 전용 PostgreSQL 서비스 추가
- [ ] SonarQube 서버 최초 실행 및 토큰 발급
  - `docker compose up -d sonar-db sonarqube`
  - `http://localhost:9000` → admin/admin 로그인 → 비밀번호 변경 → My Account → Security → Generate Token
- [ ] 분석 실행: `./gradlew test sonar -Dsonar.token=<발급토큰>`
- [ ] 분석 결과 Critical/Major 이슈 목록 이 문서에 추가 및 수정
