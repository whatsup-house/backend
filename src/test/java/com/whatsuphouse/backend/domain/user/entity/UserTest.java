package com.whatsuphouse.backend.domain.user.entity;

import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User buildUser(Integer age, LocalDate birthDate) {
        return User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("홍길동")
                .gender(Gender.MALE)
                .age(age)
                .birthDate(birthDate)
                .nickname("gildong")
                .phone("01012345678")
                .build();
    }

    @Test
    @DisplayName("생년월일이 있으면 오늘 기준 만 나이를 계산한다")
    void getCurrentAge_withBirthDate_computesKoreanAge() {
        // 생일이 하루 지난 상태 → 만 30세
        LocalDate birthDate = LocalDate.now().minusYears(30).minusDays(1);
        User user = buildUser(99, birthDate);

        assertThat(user.getCurrentAge()).isEqualTo(30);
    }

    @Test
    @DisplayName("생일이 아직 지나지 않았으면 만 나이가 한 살 적다")
    void getCurrentAge_birthdayNotYetPassed_subtractsOne() {
        // 생일이 내일 → 아직 만 29세
        LocalDate birthDate = LocalDate.now().minusYears(30).plusDays(1);
        User user = buildUser(99, birthDate);

        assertThat(user.getCurrentAge()).isEqualTo(29);
    }

    @Test
    @DisplayName("생년월일이 없으면 저장된 age로 폴백한다")
    void getCurrentAge_withoutBirthDate_fallsBackToAge() {
        User user = buildUser(25, null);

        assertThat(user.getCurrentAge()).isEqualTo(25);
    }
}
