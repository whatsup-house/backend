package com.whatsuphouse.backend.domain.user.repository;

import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.config.TestJpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestJpaConfig.class)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("홍길동")
                .gender(Gender.MALE)
                .age(25)
                .nickname("gildong")
                .phone("01012345678")
                .build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("이메일로 유저 조회 성공")
    void findByEmail_returnsUser() {
        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getNickname()).isEqualTo("gildong");
    }

    @Test
    @DisplayName("존재하는 이메일 확인 시 true 반환")
    void existsByEmail_returnsTrue() {
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    @DisplayName("존재하는 닉네임 확인 시 true 반환")
    void existsByNickname_returnsTrue() {
        assertThat(userRepository.existsByNickname("gildong")).isTrue();
    }

    @Test
    @DisplayName("삭제된 유저 단건 조회 시 빈 Optional 반환")
    void findByIdAndDeletedAtIsNull_excludesDeleted() {
        UUID id = user.getId();
        User managedUser = userRepository.findById(id).get();
        managedUser.delete();
        em.flush();
        em.clear();

        Optional<User> result = userRepository.findByIdAndDeletedAtIsNull(id);

        assertThat(result).isEmpty();
    }
}
