package com.whatsuphouse.backend.domain.user.service;

import com.whatsuphouse.backend.domain.user.dto.request.PasswordChangeRequest;
import com.whatsuphouse.backend.domain.user.dto.request.ProfileUpdateRequest;
import com.whatsuphouse.backend.domain.user.dto.request.UserWithdrawRequest;
import com.whatsuphouse.backend.domain.user.dto.response.ProfileResponse;
import com.whatsuphouse.backend.domain.user.dto.response.UserWithdrawResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        user = User.builder()
                .email("test@example.com")
                .password("encoded")
                .name("홍길동")
                .gender(Gender.MALE)
                .age(25)
                .nickname("gildong")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    // ── changePassword() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 변경된다")
    void changePassword_success() {
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("currentPw1!", "encoded")).willReturn(true);
        given(passwordEncoder.encode("newPw1234!")).willReturn("newEncoded");

        userService.changePassword(userId, PasswordChangeRequest.builder()
                .currentPassword("currentPw1!").newPassword("newPw1234!").build());

        assertThat(user.getPassword()).isEqualTo("newEncoded");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 INVALID_PASSWORD 예외")
    void changePassword_wrongCurrent_throwsException() {
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, PasswordChangeRequest.builder()
                .currentPassword("wrong").newPassword("newPw1234!").build()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);

        assertThat(user.getPassword()).isEqualTo("encoded");
    }

    // ── getProfile() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("활성 유저 프로필 조회 성공")
    void getProfile_success() {
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));

        ProfileResponse response = userService.getProfile(userId);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getNickname()).isEqualTo("gildong");
        assertThat(response.getMileage()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 유저 프로필 조회 시 예외 발생")
    void getProfile_userNotFound_throwsException() {
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ── updateProfile() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("닉네임 변경 포함 프로필 수정 성공")
    void updateProfile_success() {
        ProfileUpdateRequest request = buildUpdateRequest("newgildong");
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("newgildong")).willReturn(false);

        ProfileResponse response = userService.updateProfile(userId, request);

        assertThat(response.getNickname()).isEqualTo("newgildong");
        assertThat(response.getMileage()).isZero();
    }

    @Test
    @DisplayName("동일 닉네임 유지 시 중복 확인 없이 수정 성공")
    void updateProfile_sameNickname_success() {
        ProfileUpdateRequest request = buildUpdateRequest("gildong");
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));

        ProfileResponse response = userService.updateProfile(userId, request);

        assertThat(response.getNickname()).isEqualTo("gildong");
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 수정 시 예외 발생")
    void updateProfile_duplicateNickname_throwsException() {
        ProfileUpdateRequest request = buildUpdateRequest("taken");
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("taken")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);
    }

    // ── isEmailAvailable() ───────────────────────────────────────────────────

    @Test
    @DisplayName("사용 가능한 이메일이면 true 반환")
    void isEmailAvailable_available_returnsTrue() {
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);

        assertThat(userService.isEmailAvailable("new@example.com")).isTrue();
    }

    @Test
    @DisplayName("이미 사용 중인 이메일이면 false 반환")
    void isEmailAvailable_taken_returnsFalse() {
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThat(userService.isEmailAvailable("test@example.com")).isFalse();
    }

    // ── isNicknameAvailable() ────────────────────────────────────────────────

    @Test
    @DisplayName("사용 가능한 닉네임이면 true 반환")
    void isNicknameAvailable_available_returnsTrue() {
        given(userRepository.existsByNickname("newnick")).willReturn(false);

        assertThat(userService.isNicknameAvailable("newnick")).isTrue();
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 false 반환")
    void isNicknameAvailable_taken_returnsFalse() {
        given(userRepository.existsByNickname("gildong")).willReturn(true);

        assertThat(userService.isNicknameAvailable("gildong")).isFalse();
    }

    // ── withdraw() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("회원탈퇴 성공 시 delete 값을 Y로 변경하고 refreshToken을 삭제한다")
    void withdraw_success() {
        UserWithdrawRequest request = UserWithdrawRequest.builder()
                .password("password123!")
                .build();
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded")).willReturn(true);

        UserWithdrawResponse response = userService.withdraw(userId, request);

        assertThat(response.isWithdrawn()).isTrue();
        assertThat(response.getDeleted()).isEqualTo("Y");
        assertThat(user.getDeleteYn()).isEqualTo("Y");
        verify(redisTemplate).delete("refresh:" + userId);
    }

    @Test
    @DisplayName("회원탈퇴 비밀번호가 틀리면 예외 발생")
    void withdraw_wrongPassword_throwsException() {
        UserWithdrawRequest request = UserWithdrawRequest.builder()
                .password("wrongPassword!")
                .build();
        given(userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, "N")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPassword!", "encoded")).willReturn(false);

        assertThatThrownBy(() -> userService.withdraw(userId, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);
        verify(redisTemplate, never()).delete("refresh:" + userId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ProfileUpdateRequest buildUpdateRequest(String nickname) {
        return ProfileUpdateRequest.builder()
                .nickname(nickname).phone("01087654321").name("홍길동")
                .gender(Gender.MALE).age(25).build();
    }
}
