---
name: testing
description: 이 프로젝트 테스트 작성 지침 - Service 단위 테스트, Repository 슬라이스 테스트
---

# 테스트 작성 지침

패키지: `com.whatsuphouse.backend`
빌드: `./gradlew test` / 단일: `./gradlew test --tests "com.whatsuphouse.backend.{패키지}.{클래스명}"`

## Service 단위 테스트 패턴

```java
@ExtendWith(MockitoExtension.class)
class {도메인}ServiceTest {

    @Mock
    private {도메인}Repository {변수명}Repository;

    @InjectMocks
    private {도메인}Service {변수명}Service;

    private UUID id;
    private {도메인} entity;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        entity = {도메인}.builder()
                // 필수 필드 초기화
                .build();
    }

    @Test
    @DisplayName("한국어로 테스트 설명")
    void methodName_condition_result() {
        // given
        given({변수명}Repository.findByIdAndDeletedAtIsNull(id))
                .willReturn(Optional.of(entity));

        // when
        {ResponseType} result = {변수명}Service.get{도메인}(id);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 ID → CustomException")
    void get{도메인}_notFound_throwsCustomException() {
        // given
        given({변수명}Repository.findByIdAndDeletedAtIsNull(id))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> {변수명}Service.get{도메인}(id))
                .isInstanceOf(CustomException.class);
    }
}
```

## Repository 슬라이스 테스트 패턴

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class {도메인}RepositoryTest {

    @Autowired
    private {도메인}Repository {변수명}Repository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void setUp() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("soft delete된 항목은 조회에서 제외")
    void findAllByDeletedAtIsNull_excludesDeleted() {
        // given
        {도메인} entity = {도메인}.builder()...build();
        em.persistAndFlush(entity);
        entity.delete();
        em.flush();

        // when
        List<{도메인}> result = {변수명}Repository.findAllByDeletedAtIsNull();

        // then
        assertThat(result).isEmpty();
    }
}
```

## Controller 슬라이스 테스트 패턴

```java
@WebMvcTest({도메인}Controller.class)
class {도메인}ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private {도메인}Service {변수명}Service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    @DisplayName("정상 조회 → 200 OK")
    void get{도메인}_success_returns200() throws Exception {
        // given
        given({변수명}Service.get{도메인}(any())).willReturn(...);

        // when & then
        mockMvc.perform(get("/api/{경로}/{id}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }
}
```

## 테스트 규칙

- 테스트명: `@DisplayName("한국어 설명")` + `methodName_condition_result` 메서드명
- AAA 패턴: `// given`, `// when`, `// then` 주석 필수
- UUID ID 사용 (`UUID.randomUUID()`)
- BDDMockito 스타일: `given(...).willReturn(...)`, `willThrow(...)`, `willDoNothing()`
- 예외 검증: `assertThatThrownBy(...).isInstanceOf(CustomException.class)`
- Service 테스트: Repository만 Mock, 외부 의존성만 Mock
- Repository 테스트: 실제 DB 연결 (`Replace.NONE`), `@Import(TestJpaConfig.class)` 필수

## Quick Reference

| 어노테이션 | 용도 |
|-----------|------|
| `@ExtendWith(MockitoExtension.class)` | Service 단위 테스트 |
| `@DataJpaTest` + `@Import(TestJpaConfig.class)` | Repository 슬라이스 테스트 |
| `@WebMvcTest` | Controller 슬라이스 테스트 |
| `@SpringBootTest` | 전체 컨텍스트 통합 테스트 |
| `@MockBean` | Spring 컨텍스트에 Mock 빈 등록 |
| `@WithMockUser` | Security 테스트용 인증 사용자 |
| `@ActiveProfiles("test")` | test 프로파일 활성화 |

## Best Practices

- AAA 패턴 준수
- happy path + 실패 케이스 모두 작성
- soft delete 제외 조건 반드시 테스트
- Security 인가 규칙 테스트
- 테스트 간 독립성 유지 (`@BeforeEach`에서 상태 초기화)
