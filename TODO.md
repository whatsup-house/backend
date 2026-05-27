## 1. 해결해야할 트러블 슈팅

### LazyInitializationException — 이메일 발송 시 Gathering 프록시 초기화 실패

**증상**
`PATCH /api/admin/applications/{id}/status` 로 CONFIRMED 변경 시 아래 에러:
```
LazyInitializationException: Could not initialize proxy [Gathering#c2000000-...] - no session
```

**원인**
- `ApplicationRepository.findByIdAndDeletedAtIsNull()`에 `@EntityGraph` 없음
- `application.getGathering()`이 Lazy 프록시인 채로 이벤트에 실림
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` → 별도 스레드에서 세션이 이미 닫혀있어 `getGathering().getTitle()` 호출 시 터짐

**해결 (권장)**
`ApplicationRepository.java` 36번째 줄 `findByIdAndDeletedAtIsNull()`에 추가:
```java
@EntityGraph(attributePaths = {"gathering", "user"})
Optional<Application> findByIdAndDeletedAtIsNull(UUID id);
```

**재현**
1. Swagger 로그인: `admin@whatsuphouse.com / Test1234!`
2. `PATCH /api/admin/applications/f0000001-0000-0000-0000-000000000007/status` → `{ "status": "CONFIRMED" }`


## 2. SonarQube 정적 분석 (이후 단계)

- [x] SonarQube 연동 설정 (`build.gradle` 플러그인 — sonarqube 6.0.1.5171, jacoco)
- [x] `docker-compose.yml`에 SonarQube + 전용 PostgreSQL 서비스 추가
- [ ] SonarQube 서버 최초 실행 및 토큰 발급
  - `docker compose up -d sonar-db sonarqube`
  - `http://localhost:9000` → admin/admin 로그인 → 비밀번호 변경 → My Account → Security → Generate Token
- [ ] 분석 실행: `./gradlew test sonar -Dsonar.token=<발급토큰>`
- [ ] 분석 결과 Critical/Major 이슈 목록 이 문서에 추가 및 수정
