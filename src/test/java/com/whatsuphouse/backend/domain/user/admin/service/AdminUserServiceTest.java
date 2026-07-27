package com.whatsuphouse.backend.domain.user.admin.service;

import com.whatsuphouse.backend.domain.user.admin.dto.response.UserPageResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserApplicationStatsProjection;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("홍길동")
                .gender(Gender.MALE)
                .age(25)
                .nickname("gildong")
                .phone("01012345678")
                .build();
    }

    @Test
    @DisplayName("search가 빈 문자열이면 그대로 전달하여 전체 조회")
    void listUsers_blankSearchPassedThrough() {
        Page<UserApplicationStatsProjection> rawPage = new PageImpl<>(
                Collections.singletonList(new UserApplicationStatsProjection(user, 3L, 2L)),
                PageRequest.of(0, 20), 1);

        given(userRepository.findUsersWithApplicationStats(eq("   "), any())).willReturn(rawPage);

        UserPageResponse result = adminUserService.listUsers("   ", 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTotalApplications()).isEqualTo(3L);
        assertThat(result.getContent().get(0).getAttendedCount()).isEqualTo(2L);
        assertThat(result.getContent().get(0).getMileage()).isZero();
    }

    @Test
    @DisplayName("정상 조회 시 페이지 정보와 회원 목록 반환")
    void listUsers_returnsPageResponse() {
        Page<UserApplicationStatsProjection> rawPage = new PageImpl<>(
                Collections.singletonList(new UserApplicationStatsProjection(user, 5L, 1L)),
                PageRequest.of(0, 20), 1);

        given(userRepository.findUsersWithApplicationStats(any(), any())).willReturn(rawPage);

        UserPageResponse result = adminUserService.listUsers("gildong", 0, 20);

        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getNickname()).isEqualTo("gildong");
        assertThat(result.getContent().get(0).getMileage()).isZero();
    }
}
