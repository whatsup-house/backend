# Naming 컨벤션

이 문서는 클래스명, 필드명, 메서드명에 대한 네이밍 규칙을 정의한다.

---

## 1. 클래스명

| 레이어 | 형식 | 예시 |
|--------|------|------|
| Entity | 도메인명 (단수) | `User`, `Gathering` |
| Controller (유저) | `{도메인}Controller` | `GatheringController` |
| Controller (관리자) | `Admin{도메인}Controller` | `AdminGatheringController` |
| Service (유저) | `{도메인}Service` | `GatheringService` |
| Service (관리자) | `Admin{도메인}Service` | `AdminGatheringService` |
| Repository | `{도메인}Repository` | `GatheringRepository` |
| Request DTO | `{동작}{도메인}Request` | `GatheringCreateRequest` |
| Response DTO | `{도메인}{범위}Response` | `GatheringDetailResponse` |
| Enum | 대문자 단수형 | `GatheringStatus` |

- `Dto` 접미사 사용 금지 (`GatheringDto` → `GatheringResponse`)
- 관리자/유저 분리 시 패키지로 분리 (`admin/`, `client/`)

---

## 2. 필드명

- Java 필드: **camelCase** / DB 컬럼: **snake_case**
- boolean: `is` 접두어 (`isAdmin`, `isAvailable`)
- 날짜/시간: `{의미}At` (`createdAt`, `deletedAt`), `{의미}Date`, `{의미}Time`
- 연관 엔티티: 객체명 그대로 (`location`, `user`)
- 외래키만 저장: `{도메인}Id` (`locationId`)

---

## 3. Enum 값

- **UPPER_SNAKE_CASE**, 의미 명확한 단어

```java
public enum GatheringStatus { OPEN, CLOSED, COMPLETED, CANCELLED }
public enum ApplicationStatus { PENDING, CONFIRMED, CANCELLED, ATTENDED }
```

---

## 4. Service 메서드명

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

---

## 5. Repository 메서드명

- Soft delete 조회 시 `AndDeletedAtIsNull` 조건 필수
- 단건: `findBy{조건}AndDeletedAtIsNull`
- 전체: `findAllBy{조건}AndDeletedAtIsNull`
- 존재: `existsBy{필드}`

```java
Optional<User> findByIdAndDeletedAtIsNull(UUID id);
List<Gathering> findAllByDeletedAtIsNull();
boolean existsByEmail(String email);
```

---

## 6. 패키지 구조

```
domain/{name}/
  controller/ (admin/, client/ 분리 가능)
  service/
  repository/
  entity/
  dto/request/, dto/response/
```

---

## 7. 금지

- 약어 남발 (`usrSvc`, `gthrCtrl`)
- 숫자 접미 (`Gathering2`, `UserNew`)
- `Manager`, `Helper`, `Utils` 접미어를 Service 대신 사용
