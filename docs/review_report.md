# 배포 전 코드 리뷰 보고서

> 리뷰 일자: 2026-05-19
> 대상: 전체 도메인 + global 패키지 (배포 전 최종 점검)

---

## Critical — 즉시 수정 필요

### 1. `deleteApplication` soft delete 경계 불명확
**파일:** `domain/application/admin/service/AdminApplicationService.java`

`deleteApplication()`에서 `findById()`를 사용해 이미 논리 삭제된 데이터도 조회된다.
soft delete 조회 일관성 규칙 위반으로, 삭제된 신청 건이 재노출될 수 있다.

**개선:** `findByIdAndDeletedAtIsNull()`로 교체하거나 삭제 전용 Repository 메서드를 분리한다.

### 2. Unreachable code 존재

도달 불가능한 코드 블록이 존재해 의도하지 않은 동작 유발 가능성 있음.

---

## High — 배포 전 수정 권장

### 3. 마일리지 중복 지급 Race Condition
**파일:** `domain/mileage/service/MileageService.java`

마일리지 지급/차감 시 동시 요청에 대한 동시성 처리 누락.
`@Lock` 또는 낙관적 잠금 없이 읽기-수정-쓰기 패턴이 사용되어 중복 지급이 발생할 수 있다.

**개선:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` 또는 `version` 필드를 이용한 낙관적 잠금 적용.

### 4. 인증 실패 응답 형식 불일치
**파일:** `global/config/SecurityConfig.java`

`authenticationEntryPoint`가 Servlet 기본 에러 형식으로 응답한다.
다른 API 오류는 `ApiResult<Void>` 형태인데 인증 실패만 포맷이 다르다.

**개선:** `authenticationEntryPoint`에서 `ObjectMapper`로 `ApiResult.fail(...)` JSON을 직접 write한다.

### 5. 페이징 미처리 — 대량 데이터 반환 위험

일부 목록 조회 API에 페이징 없이 전체 데이터를 반환.
데이터 증가 시 OOM 또는 응답 지연이 발생할 수 있다.

**개선:** `Pageable` 파라미터 도입 및 `Page<T>` 응답으로 전환.

### 6. 목록 API `size` 상한 없음

클라이언트가 `size=10000` 같은 큰 값을 요청할 경우 제한 없이 처리된다.

**개선:** `@Max(100)` 등의 Validation 또는 Service 레이어에서 상한 강제.

### 7. N+1 쿼리 — 사용자 신청 목록
**파일:** `domain/application/repository/ApplicationRepositoryCustomImpl.java`, 라인 34-38

`application.getGathering().getId()`, `application.getUser().getId()` 호출 시
LAZY 로딩으로 N+1 발생.

**개선:**
```
join(application.gathering).fetchJoin()
join(application.user).fetchJoin()
```
을 쿼리에 추가한다.

---

## Medium

### 8. 코드 중복 — 검증 로직
여러 Service 계층에서 유사한 검증 로직이 반복됨. 공통 헬퍼 또는 도메인 메서드로 추출 필요.

### 9. `user` null 가능성 — LAZY 로딩
일부 엔티티에서 `user`가 null인 경우 처리 없이 LAZY 로딩 접근.
`NullPointerException` 또는 `LazyInitializationException` 발생 가능.

### 10. 이미지 폴더 검증 누락
**파일:** `domain/image/controller/ImageController.java`

업로드 폴더 경로 값을 별도 검증 없이 사용해 Path Traversal 위험 가능성.

**개선:** 허용 폴더 목록(`enum` 또는 whitelist)을 정의하고 입력값을 검증한다.

### 11. 응답 패턴 불일치
일부 Controller가 `ResponseEntity<ApiResult<>>`, 일부는 직접 객체를 반환하는 혼재 방식.
클라이언트 처리 로직이 API마다 달라져야 하는 문제 발생.

---

## Low

### 12. 관리자 신청 목록 N+1
**파일:** `domain/application/admin/service/AdminApplicationService.java`, 라인 47-48

`application.getGathering().getId()`, `application.getUser().getId()` LAZY 로딩으로 N+1 발생.

**개선:** `AdminApplicationService`에서 사용하는 Repository 쿼리에 `fetchJoin()` 추가.

### 13. 스케줄러 로그에 예외 정보 누락
**파일:** `global/storage/StorageCleanupScheduler.java`, 라인 54-57

```java
// 현재
log.warn("temp 파일 정리 실패: prefix={}", prefix);

// 개선
log.warn("temp 파일 정리 실패: prefix={}", prefix, e);
```

예외 객체 `e`가 로그에 포함되지 않아 원인 파악이 어렵다.

---

## 요약

| 중요도 | 건수 | 핵심 이슈 |
|--------|------|-----------|
| Critical | 2 | soft delete 불명확, unreachable code |
| High | 5 | Race condition, 인증 응답 불일치, 페이징 없음, N+1 |
| Medium | 4 | 코드 중복, null 위험, 폴더 검증 없음, 응답 형식 혼재 |
| Low | 2 | N+1(관리자), 스케줄러 로그 |

---

## 수정 우선순위

1. **Race Condition (마일리지)** — 데이터 정합성 직결, 즉시 수정
2. **N+1 쿼리** — 성능 직결, 즉시 수정
3. **인증 실패 응답 형식 불일치** — 클라이언트 혼선, 배포 전 수정
4. **soft delete 경계** — 데이터 무결성, 배포 전 수정
5. **페이징 / size 상한** — 운영 안정성
6. **폴더 검증 누락** — 보안
7. **응답 패턴 통일, 로그 보완** — 운영 편의
