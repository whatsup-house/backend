package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.dto.request.FindEmailRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.LoginRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.PasswordResetRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.RegisterRequest;
import com.whatsuphouse.backend.domain.auth.dto.response.FindEmailResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.PasswordResetConfirmResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.PasswordResetRequestResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.RegisterResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.notification.NotificationService;
import com.whatsuphouse.backend.domain.notification.event.WelcomeEvent;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.auth.JwtTokenProvider;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String PASSWORD_RESET_PREFIX = "password-reset:";
    private static final String ACTIVE_USER = "N";
    private static final long PASSWORD_RESET_TTL_MINUTES = 30L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final MileageService mileageService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    @Value("${app.frontend-url:https://www.whatsup.house}")
    private String frontendUrl;

    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .gender(request.getGender())
                .age(request.getAge())
                .nickname(request.getNickname())
                .phone(request.getPhone())
                .build();

        userRepository.save(user);
        mileageService.rewardSignup(user);
        // 트랜잭션 커밋 후 환영 이메일 발송
        eventPublisher.publishEvent(new WelcomeEvent(user));
        return RegisterResponse.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeleteYn(request.getEmail(), ACTIVE_USER)
                .filter(u -> u.getDeletedAt() == null && !u.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.isAdmin());
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .isAdmin(user.isAdmin())
                        .mileage(user.getMileageBalance())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public FindEmailResponse findEmail(FindEmailRequest request) {
        User user = userRepository.findFirstByNameAndPhoneAndDeleteYnOrderByCreatedAtDesc(
                        request.getName(),
                        request.getPhone(),
                        ACTIVE_USER
                )
                .filter(u -> u.getDeletedAt() == null && !u.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return FindEmailResponse.builder()
                .maskedEmail(maskEmail(user.getEmail()))
                .build();
    }

    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailAndDeleteYn(request.getEmail(), ACTIVE_USER)
                .filter(u -> u.getDeletedAt() == null && !u.isWithdrawn())
                .ifPresent(user -> {
                    String token = UUID.randomUUID().toString();
                    redisTemplate.opsForValue().set(
                            PASSWORD_RESET_PREFIX + token,
                            user.getId().toString(),
                            PASSWORD_RESET_TTL_MINUTES,
                            TimeUnit.MINUTES
                    );
                    notificationService.sendPasswordReset(user, buildPasswordResetUrl(token));
                });

        return PasswordResetRequestResponse.builder()
                .accepted(true)
                .build();
    }

    public PasswordResetConfirmResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        String redisKey = PASSWORD_RESET_PREFIX + request.getToken();
        String userId = redisTemplate.opsForValue().get(redisKey);

        if (userId == null) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }

        User user = userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(UUID.fromString(userId), ACTIVE_USER)
                .filter(u -> !u.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        redisTemplate.delete(redisKey);
        logout(user.getId());

        return PasswordResetConfirmResponse.builder()
                .reset(true)
                .build();
    }

    public void logout(UUID userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    public TokenRefreshResponse refresh(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        try {
            jwtTokenProvider.validateToken(refreshToken);
        } catch (CustomException e) {
            ErrorCode code = e.getErrorCode() == ErrorCode.TOKEN_EXPIRED
                    ? ErrorCode.EXPIRED_REFRESH_TOKEN
                    : ErrorCode.INVALID_REFRESH_TOKEN;
            throw new CustomException(code);
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String stored = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);

        if (stored == null || !stored.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findByIdAndDeletedAtIsNullAndDeleteYn(userId, ACTIVE_USER)
                .filter(u -> !u.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.isAdmin());
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), newRefreshToken);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private void saveRefreshToken(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                jwtTokenProvider.getRefreshExpiration(),
                TimeUnit.MILLISECONDS
        );
    }

    private String buildPasswordResetUrl(String token) {
        String baseUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        return baseUrl + "/password-reset/confirm?token=" + token;
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "*" + domain;
        }

        return localPart.charAt(0)
                + "*".repeat(localPart.length() - 2)
                + localPart.charAt(localPart.length() - 1)
                + domain;
    }
}
