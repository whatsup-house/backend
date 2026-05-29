# KAN-141 도메인별 점검 로그

> 점검 범위: Controller → Service → Repository → Entity → DTO 전 레이어

---

## auth

### 변경 사항

#### 1. `RegisterRequest` — 선택 입력 필드 추가
`instagramId`, `mbti`, `job`, `intro` 4개 필드 추가.
기존엔 회원가입 시점에 저장 불가했던 정보 (프로필 수정 API로만 입력 가능했음).

#### 2. `User` — 빌더 생성자 확장
위 4개 필드를 생성자 파라미터에 추가.

#### 3. `AuthService`

**클래스 레벨 `@Transactional` 제거 → 메서드별 명시**

| 메서드 | 변경 후 |
|--------|---------|
| `register()` | `@Transactional` |
| `login()` | `@Transactional(readOnly = true)` |
| `logout()` | 없음 (Redis만 사용) |
| `refresh()` | `@Transactional(readOnly = true)` |

**`register()` — 신규 필드 빌더에 전달**
instagramId, mbti, job, intro를 `User.builder()`에 추가.

**`refresh()` — 중복 파싱 제거**
`validateToken()` + `getUserIdFromToken()` 2번 파싱 → `getUserIdFromToken()` 단일 호출로 통합.

#### 4. `JwtTokenProvider` — `parseClaims()` 추출
파싱 로직을 private `parseClaims()`로 통합.
`validateToken()`, `getUserIdFromToken()`, `getUserPrincipal()` 세 메서드가 공유.
`validateToken()`은 `JwtAuthFilter` 표준 패턴용으로 유지.

---

## user

> 진행 예정

---
