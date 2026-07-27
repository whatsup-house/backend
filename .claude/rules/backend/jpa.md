# JPA Rules

이 문서는 JPA 엔티티, 연관관계, 조회, 저장 로직을 다룰 때 따라야 할 규칙을 정의한다.

---

## 1. 엔티티 설계

- 엔티티는 상태와 행위를 함께 가지는 도메인 객체로 다룬다. 단순 데이터 보관 객체가 아니다.
- 비즈니스적으로 의미 있는 상태 변경 메서드 사용 (`changeStatus()`, `approve()` 등). setter 지양.
- JPA 기본 생성자는 `protected`. 핵심 값은 생성자 또는 정적 팩토리 메서드로 받는다.
- `BaseEntity` 상속 (soft delete: `deletedAt` 필드 활용).
- 엔티티 변경은 DB 스키마 및 기존 쿼리에 영향을 줄 수 있으므로 신중하게 다룬다.

---

## 2. DTO와 엔티티 분리

- 엔티티를 API 요청/응답으로 직접 사용하지 않는다. Controller에서 엔티티 직접 반환 금지.
- Response DTO: `from(entity)` 패턴 허용.
- Request DTO: 단순 생성만 `toEntity()` 허용. 복잡한 조합은 Service에서.
- 엔티티에 `toDto()`, `fromDto()` 메서드를 두지 않는다.

---

## 3. 연관관계

- 꼭 필요한 경우에만 추가. 기본 단방향 우선.
- 양방향은 꼭 필요할 때만. 연관관계 주인 명확히 관리.
- `@ManyToMany` 직접 사용 금지 → 연결 엔티티로 분리.
- 컬렉션 연관관계는 정말 필요한 경우에만.

---

## 4. Fetch 전략

- ToOne: 명시적 LAZY 사용. EAGER 기본값에 의존 금지.
- ToMany: LAZY 유지.
- 필요한 데이터는 fetch join, DTO 조회, 별도 조회 전략으로 가져온다.
- "연관관계를 걸어두면 알아서 가져오겠지"라고 가정하지 않는다.

---

## 5. 조회

- 목록 조회: 필요한 필드만 DTO 조회 우선.
- 상세 조회: 필요한 연관 데이터 범위를 명확히 한 뒤 조회.
- N+1 가능성 있으면 fetch join 또는 조회 구조 변경 먼저 검토.
- soft delete 데이터가 조회에 섞이지 않도록 주의 (`deletedAt = null` 조건).
- Controller 바깥에서 지연 로딩이 우연히 터지지 않도록 한다.

---

## 6. 저장/수정

- 상태 변경은 엔티티 메서드로 수행. Service는 유스케이스 흐름과 트랜잭션 관리.
- 영속 엔티티의 변경은 변경 감지 우선 활용.
- 생성과 수정 흐름을 구분.

---

## 7. Cascade / orphanRemoval

- Cascade는 Aggregate 경계가 명확하고 함께 저장/삭제될 때만 사용.
- 무분별한 `CascadeType.ALL` 지양.
- `orphanRemoval = true`는 부모-자식 생명주기가 강하게 묶일 때만.

---

## 8. 트랜잭션

- 트랜잭션 경계는 Service에서 관리. Controller에서 다루지 않는다.
- 읽기 전용 조회는 `readOnly` 트랜잭션 고려. 범위를 불필요하게 넓히지 않는다.

---

## 9. 예외 및 검증

- 자기 상태만으로 판단 가능한 검증은 엔티티에서 처리.
- 다른 엔티티 조회나 외부 의존이 필요한 검증은 Service에서.
- `CustomException(ErrorCode)` 사용.
