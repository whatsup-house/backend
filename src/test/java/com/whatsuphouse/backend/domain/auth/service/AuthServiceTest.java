package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.dto.request.FindEmailRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.LoginRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.PasswordResetRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.RegisterRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.GuestEmailVerificationRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.GuestEmailVerificationConfirmRequest;
import com.whatsuphouse.backend.domain.auth.dto.response.GuestEmailVerificationResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.FindEmailResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.PasswordResetConfirmResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.PasswordResetRequestResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.RegisterResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.notification.NotificationService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private MileageService mileageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;

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
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");
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
        doAnswer(invocation -> {
            User signupUser = invocation.getArgument(0);
            signupUser.addMileage(MileageService.SIGNUP_REWARD_AMOUNT);
            return null;
        }).when(mileageService).rewardSignup(any(User.class));

        RegisterResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getMileageRewarded()).isEqualTo(1000);
        assertThat(response.getMileageBalance()).isEqualTo(1000);
        verify(mileageService).rewardSignup(any(User.class));
    }

    @Test
    @DisplayName("회원가입 시 생년월일이 저장된다")
    void register_persistsBirthDate() {
        RegisterRequest request = RegisterRequest.builder()
                .email("birth@example.com").password("password123!").name("홍길동")
                .gender(Gender.MALE).age(25).nickname("birthnick")
                .birthDate(LocalDate.of(1999, 3, 15)).build();
        given(userRepository.existsByEmail("birth@example.com")).willReturn(false);
        given(userRepository.existsByNickname("birthnick")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encodedPassword");
        given(userRepository.save(any())).willReturn(user);

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getBirthDate()).isEqualTo(LocalDate.of(1999, 3, 15));
    }

    @Test
    @DisplayName("이미 사용 중인 이메일로 가입하면 예외 발생")
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = buildRegisterRequest("test@example.com", "nickname1");
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(mileageService, never()).rewardSignup(any(User.class));
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
        verify(mileageService, never()).rewardSignup(any(User.class));
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그인")
    void login_success() {
        LoginRequest request = buildLoginRequest("test@example.com", "password123!");
        given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(any())).willReturn("accessToken");
        given(jwtTokenProvider.generateRefreshToken(any())).willReturn("refreshToken");
        given(jwtTokenProvider.getRefreshExpiration()).willReturn(86400000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
        assertThat(response.getUser().getMileage()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 예외 발생")
    void login_userNotFound_throwsException() {
        LoginRequest request = buildLoginRequest("none@example.com", "password123!");
        given(userRepository.findByEmailAndDeletedAtIsNull("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("삭제된 계정으로 로그인하면 예외 발생")
    void login_deletedUser_throwsException() {
        user.delete();
        LoginRequest request = buildLoginRequest("test@example.com", "password123!");
        given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외 발생")
    void login_wrongPassword_throwsException() {
        LoginRequest request = buildLoginRequest("test@example.com", "wrongPassword!");
        given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).willReturn(Optional.of(user));
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
        given(jwtTokenProvider.getUserIdFromToken("validRefreshToken")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn("validRefreshToken");
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
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
        doThrow(new CustomException(ErrorCode.INVALID_TOKEN))
                .when(jwtTokenProvider).validateToken("invalidToken");

        assertThatThrownBy(() -> authService.refresh("invalidToken"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Redis에 저장된 토큰과 다르면 예외 발생")
    void refresh_tokenMismatch_throwsException() {
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
        given(jwtTokenProvider.getUserIdFromToken("validRefreshToken")).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn("validRefreshToken");
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("validRefreshToken"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ── findEmail() / password reset ────────────────────────────────────────

    @Test
    @DisplayName("이름과 전화번호로 마스킹된 이메일을 찾는다")
    void findEmail_success() {
        FindEmailRequest request = FindEmailRequest.builder()
                .name("홍길동")
                .phone("01012345678")
                .build();
        given(userRepository.findFirstByNameAndPhoneAndDeletedAtIsNullOrderByCreatedAtDesc("홍길동", "01012345678"))
                .willReturn(Optional.of(user));

        FindEmailResponse response = authService.findEmail(request);

        assertThat(response.getMaskedEmail()).isEqualTo("t**t@example.com");
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 토큰 저장 후 이메일을 보낸다")
    void requestPasswordReset_success() {
        PasswordResetRequest request = PasswordResetRequest.builder()
                .email("test@example.com")
                .build();
        given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).willReturn(Optional.of(user));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        PasswordResetRequestResponse response = authService.requestPasswordReset(request);

        assertThat(response.isAccepted()).isTrue();
        verify(valueOperations).set(startsWith("password-reset:"), eq(userId.toString()), eq(30L), eq(TimeUnit.MINUTES));
        verify(notificationService).sendPasswordReset(eq(user), contains("/password-reset/confirm?token="));
    }

    @Test
    @DisplayName("없는 이메일의 재설정 요청도 accepted를 반환하고 메일은 보내지 않는다")
    void requestPasswordReset_unknownEmail_returnsAccepted() {
        PasswordResetRequest request = PasswordResetRequest.builder()
                .email("none@example.com")
                .build();
        given(userRepository.findByEmailAndDeletedAtIsNull("none@example.com")).willReturn(Optional.empty());

        PasswordResetRequestResponse response = authService.requestPasswordReset(request);

        assertThat(response.isAccepted()).isTrue();
        verify(notificationService, never()).sendPasswordReset(any(), any());
    }

    @Test
    @DisplayName("재설정 토큰으로 비밀번호를 변경하고 토큰을 무효화한다")
    void confirmPasswordReset_success() {
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .token("reset-token")
                .newPassword("newPassword123!")
                .build();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("password-reset:reset-token")).willReturn(userId.toString());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPassword123!")).willReturn("encodedNewPassword");

        PasswordResetConfirmResponse response = authService.confirmPasswordReset(request);

        assertThat(response.isReset()).isTrue();
        assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
        verify(redisTemplate).delete("password-reset:reset-token");
        verify(redisTemplate).delete("refresh:" + userId);
    }

    @Test
    @DisplayName("비회원 이메일 인증번호를 5분 TTL로 저장하고 발송한다")
    void requestGuestEmailVerification_storesAndSendsCode() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        GuestEmailVerificationResponse response = authService.requestGuestEmailVerification(
                GuestEmailVerificationRequest.builder().email("Guest@Test.COM").build());

        assertThat(response.isAccepted()).isTrue();
        verify(valueOperations).set(eq("guest-email-code:guest@test.com"), matches("\\d{6}"),
                eq(5L), eq(TimeUnit.MINUTES));
        verify(notificationService).sendGuestEmailVerification(eq("guest@test.com"), matches("\\d{6}"));
    }

    @Test
    @DisplayName("올바른 인증번호는 30분 인증 완료 표식으로 교환된다")
    void confirmGuestEmailVerification_marksVerified() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("guest-email-code:guest@test.com")).willReturn("123456");

        GuestEmailVerificationResponse response = authService.confirmGuestEmailVerification(
                GuestEmailVerificationConfirmRequest.builder()
                        .email("guest@test.com").code("123456").build());

        assertThat(response.isVerified()).isTrue();
        verify(redisTemplate).delete("guest-email-code:guest@test.com");
        verify(valueOperations).set("guest-email-verified:guest@test.com", "true", 30L, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("틀리거나 만료된 비회원 이메일 인증번호는 거부한다")
    void confirmGuestEmailVerification_invalidCode_throws() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("guest-email-code:guest@test.com")).willReturn(null);

        assertThatThrownBy(() -> authService.confirmGuestEmailVerification(
                GuestEmailVerificationConfirmRequest.builder()
                        .email("guest@test.com").code("123456").build()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_EMAIL_VERIFICATION_CODE);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String email, String nickname) {
        return RegisterRequest.builder()
                .email(email).password("password123!").name("홍길동")
                .gender(Gender.MALE).age(25).nickname(nickname).build();
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        return LoginRequest.builder()
                .email(email).password(password).build();
    }

}
