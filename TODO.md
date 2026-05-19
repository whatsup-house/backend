# 배포 전 리팩토링 TODO

---

## 1. 디렉토리 구조 정리

### 1-1. `image` 도메인 위치 불일치
- `domain/image/controller/ImageController.java` 는 실제로 도메인 로직이 없고 `global/storage`의 `StorageService`를 그대로 위임만 함
- `ImageController`를 `global/storage/controller/` 또는 `global/image/controller/`로 이동하거나, `domain/image`를 `global`로 흡수하는 방향 결정 필요

### 1-2. `carousel` 홈 API 컨트롤러 위치 불일치
- `CarouselController`의 매핑 경로는 `/api/home/carousel`이지만 패키지는 `domain/carousel/client/controller/`에 위치
- `HomeGatheringController`는 같은 `/api/home/**` 경로임에도 `domain/gathering/client/controller/`에 위치
- 홈 화면 API를 담당하는 컨트롤러를 한 곳(`domain/home/` 또는 `global/home/`)으로 모으거나, 경로와 패키지를 일치시키는 방향 결정 필요

### 1-3. `MileageHistory` — `BaseEntity` 미상속
- 다른 모든 Entity는 `BaseEntity`를 상속하지만 `MileageHistory`만 독자적으로 `@PrePersist`로 `earnedDate`를 관리
- `createdAt`, `updatedAt` 오디팅 필드가 없어 일관성 깨짐
- `BaseEntity` 상속으로 통일하고 `earnedDate` 필드는 제거 또는 `createdAt`으로 대체 검토

### 1-4. `global/storage` DTO 위치
- `global/storage/dto/ImageUploadResponse.java`가 storage 패키지 내에 존재
- `ImageController`가 `domain/image`에 있다면 DTO도 같은 도메인 패키지로 이동하거나, 컨트롤러를 global로 이동 후 함께 관리

---

## 2. 네이밍 컨벤션 정리

### 2-1. boolean 필드 JPA 컬럼 네이밍 혼용
- `CarouselSlide.isActive`, `Gathering.isCurated` → DB 컬럼명이 각각 `is_active`, `is_curated`로 선언
- `User.isAdmin` → 컬럼명 `is_admin`
- JPA에서 `boolean` 필드에 `is` 접두어를 붙이면 getter가 `isActive()`가 아닌 `getIsActive()` 또는 `active()`로 생성될 수 있어 Lombok과 혼용 시 예측이 어려움
- 프로젝트 내 boolean 필드 접두어 규칙 통일 (`active`, `curated`, `admin`으로 통일하거나 현재 방식 고수 중 결정)

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

### 3-3. `GatheringService` — 필터 조건 분기 중복
- `GatheringService.getGatherings()`와 `AdminGatheringService.resolveGatherings()`가 동일한 날짜/상태 필터 조합 분기를 각각 구현
- 클라이언트용은 날짜 단일 필터, 어드민용은 날짜 범위 필터까지 포함하는 차이가 있으므로 현재 분리 유지가 맞지만, `GatheringRepository`에 Querydsl Custom 쿼리로 단일화하는 방향도 검토 가능

### 3-4. 테스트 DTO 생성 방식 — `ReflectionTestUtils.setField` 사각지대
- `ReviewServiceTest.java` — DTO(`request`, `updateRequest` 등)에 `setField` 사용 중 → Builder 전환 필요
- `Entity.id` 주입(`setField(entity, "id", ...)`)은 JPA 관리 필드라 대상 아님

---

## 4. 코드 리뷰 보고서 이슈 (`docs/review_report.md`)

> 진행 기준: 우선순위 8개 항목 순서대로 처리

- [x] 1번 — `SecurityConfig.anyRequest().permitAll()` → `denyAll()` (커밋: `fix: SecurityConfig anyRequest().permitAll() → denyAll() 보안 정책 강화`)
- [x] 2번 — N+1 쿼리 (`ApplicationRepositoryCustomImpl` fetch join, `ApplicationRepository` / `GatheringRepository` `@EntityGraph`) (커밋: `fix: N+1 쿼리 해결 — fetch join 및 @EntityGraph 추가`)
- [ ] 3번 — `User.updateProfile()` null 덮어쓰기 — null 체크 후 조건부 업데이트 (`User.java`)
- [ ] 4번 — soft delete 조회 일관성 — `AdminApplicationService.deleteApplication()`의 `findById()` → `findByIdAndDeletedAtIsNull()` 교체
- [ ] 5번 — 토큰 만료/위변조 미구분 — `JwtTokenProvider.validateToken()`에서 `ExpiredJwtException` 별도 catch, `TOKEN_EXPIRED` 에러코드 실제 사용
- [ ] 6번 — 인증 실패 응답 포맷 불일치 — `authenticationEntryPoint`에서 `ApiResult.fail(...)` JSON으로 응답
- [ ] 7번 — 토큰 응답 바디 노출 — `LoginResponse`에서 토큰 필드 제거, 쿠키로만 전달
- [ ] 8번 — carousel 경로 충돌 — `PATCH /{slideId}` → `PATCH /{slideId}/active`, `PUT /order` → `PUT /reorder`

---

## 5. SonarQube 정적 분석 (이후 단계)

- [x] SonarQube 연동 설정 (`build.gradle` 플러그인 — sonarqube 6.0.1.5171, jacoco)
- [x] `docker-compose.yml`에 SonarQube + 전용 PostgreSQL 서비스 추가
- [ ] SonarQube 서버 최초 실행 및 토큰 발급
  - `docker compose up -d sonar-db sonarqube`
  - `http://localhost:9000` → admin/admin 로그인 → 비밀번호 변경 → My Account → Security → Generate Token
- [ ] 분석 실행: `./gradlew test sonar -Dsonar.token=<발급토큰>`
- [ ] 분석 결과 Critical/Major 이슈 목록 이 문서에 추가 및 수정
