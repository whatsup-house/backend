package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.dto.request.LoginRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.RegisterRequest;
import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.RegisterResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.auth.JwtTokenProvider;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .name("홍길동")
                .gender(Gender.MALE)
                .age(25)
                .nickname("gildong")
                .phone("01012345678")
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    // ── register() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 회원가입")
    void register_success() {
        RegisterRequest request = buildRegisterRequest("new@example.com", "nickname1");
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(userRepository.existsByNickname("nickname1")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encodedPassword");
        given(userRepository.save(any())).willReturn(user);

        RegisterResponse response = authService.register(request);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("이미 사용 중인 이메일로 가입하면 예외 발생")
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = buildRegisterRequest("test@example.com", "nickname1");
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 가입하면 예외 발생")
    void register_duplicateNickname_throwsException() {
        RegisterRequest request = buildRegisterRequest("new@example.com", "gildong");
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(userRepository.existsByNickname("gildong")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_NICKNAME);
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그인")
    void login_success() {
        LoginRequest request = buildLoginRequest("test@example.com", "password123!");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("accessToken");
        given(jwtTokenProvider.generateRefreshToken(any())).willReturn("refreshToken");
        given(jwtTokenProvider.getRefreshExpiration()).willReturn(86400000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 예외 발생")
    void login_userNotFound_throwsException() {
        LoginRequest request = buildLoginRequest("none@example.com", "password123!");
        given(userRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("삭제된 계정으로 로그인하면 예외 발생")
    void login_deletedUser_throwsException() {
        user.delete();
        LoginRequest request = buildLoginRequest("test@example.com", "password123!");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외 발생")
    void login_wrongPassword_throwsException() {
        LoginRequest request = buildLoginRequest("test@example.com", "wrongPassword!");
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    // ── logout() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그아웃 - Redis 토큰 삭제")
    void logout_success() {
        authService.logout(userId);

        verify(redisTemplate).delete("refresh:" + userId);
    }

    // ── refresh() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 토큰 갱신")
    void refresh_success() {
        given(jwtTokenProvider.validateToken("validRefreshToken")).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken("validRefreshToken")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn("validRefreshToken");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).willReturn("newAccessToken");
        given(jwtTokenProvider.generateRefreshToken(userId)).willReturn("newRefreshToken");
        given(jwtTokenProvider.getRefreshExpiration()).willReturn(86400000L);

        TokenRefreshResponse response = authService.refresh("validRefreshToken");

        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰이면 예외 발생")
    void refresh_invalidToken_throwsException() {
        given(jwtTokenProvider.validateToken("invalidToken")).willReturn(false);

        assertThatThrownBy(() -> authService.refresh("invalidToken"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Redis에 저장된 토큰과 다르면 예외 발생")
    void refresh_tokenMismatch_throwsException() {
        given(jwtTokenProvider.validateToken("validRefreshToken")).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken("validRefreshToken")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn("differentToken");

        assertThatThrownBy(() -> authService.refresh("validRefreshToken"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Redis에 토큰이 없으면 예외 발생")
    void refresh_tokenNotInRedis_throwsException() {
        given(jwtTokenProvider.validateToken("validRefreshToken")).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken("validRefreshToken")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn(null);

        assertThatThrownBy(() -> authService.refresh("validRefreshToken"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("토큰은 유효하나 유저가 삭제된 경우 예외 발생")
    void refresh_deletedUser_throwsException() {
        user.delete();
        given(jwtTokenProvider.validateToken("validRefreshToken")).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken("validRefreshToken")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn("validRefreshToken");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("validRefreshToken"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String email, String nickname) {
        RegisterRequest request = new RegisterRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", "password123!");
        ReflectionTestUtils.setField(request, "name", "홍길동");
        ReflectionTestUtils.setField(request, "gender", Gender.MALE);
        ReflectionTestUtils.setField(request, "age", 25);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        return request;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

}
