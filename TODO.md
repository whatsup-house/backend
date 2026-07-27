## Todo List

### 1. 테스트 코드 전략 및 구현 ✅ 전략 완료
> 전략: Service(Mockito 단위) + Repository(@DataJpaTest+H2), Controller 제외

#### Phase 0: 환경 세팅
- [x] 전략 수립 (ApplicationService, AuthService, ApplicationRepository 대상 선정)
- [x] piomin `spring-boot-engineer` 에이전트 + `spring-boot/references/testing.md` 스킬 추가
- [ ] `TestJpaConfig.java` 생성 (`@EnableJpaAuditing` for @DataJpaTest)
- [ ] `BackendApplicationTests.java` — `@MockBean StringRedisTemplate` 추가 → `./gradlew test` 통과 확인

#### Phase 1: Application 도메인 (목표 17개 테스트)
- [ ] `ApplicationServiceTest.java` — `apply()` 8개 케이스
  - 회원 정상신청, 비회원 정상신청
  - 게더링없음, 모집중아님, 정원초과
  - 회원중복신청, 비회원전화번호없음, 비회원전화번호중복
- [ ] `ApplicationServiceTest.java` — `cancel()` 5개, `checkApplication()` 2개, `getMyApplications()` 2개
- [ ] `ApplicationRepositoryTest.java` — soft-delete 복합 쿼리 6개
  - countByGatheringIdAndStatusNotAndDeletedAtIsNull
  - existsByGatheringIdAndUserIdAndDeletedAtIsNull
  - existsByGatheringIdAndPhoneAndDeletedAtIsNull
  - findByPhoneAndBookingNumberAndDeletedAtIsNull
  - findByUserIdAndDeletedAtIsNull

#### Phase 2: Auth 도메인 (목표 13개 테스트)
- [ ] `AuthServiceTest.java` — `login()` 4개, `refresh()` 5개
- [ ] `AuthServiceTest.java` — `register()` 3개, `logout()` 1개

---

### 2. 배포 운영 전략 학습하고 프로젝트 적용하기
