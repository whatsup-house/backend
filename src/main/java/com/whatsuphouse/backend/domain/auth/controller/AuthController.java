package com.whatsuphouse.backend.domain.auth.controller;

import com.whatsuphouse.backend.domain.auth.dto.request.LoginRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.FindEmailRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.PasswordResetRequest;
import com.whatsuphouse.backend.domain.auth.dto.request.RegisterRequest;
import com.whatsuphouse.backend.domain.auth.dto.response.FindEmailResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.PasswordResetConfirmResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.PasswordResetRequestResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.RegisterResponse;
import com.whatsuphouse.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.whatsuphouse.backend.domain.auth.service.AuthService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Tag(name = "인증", description = "회원가입, 로그인, 로그아웃, 토큰 갱신 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_TOKEN = "accessToken";
    private static final String REFRESH_TOKEN = "refreshToken";

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임 등 기본 정보로 회원가입한다.")
    @PostMapping("/register")
    public ResponseEntity<ApiResult<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("회원가입이 완료되었습니다.", response));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인. accessToken과 refreshToken을 HttpOnly 쿠키로 발급한다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_TOKEN, response.getAccessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_TOKEN, response.getRefreshToken()).toString())
                .body(ApiResult.success("로그인되었습니다.", response));
    }

    @Operation(summary = "로그아웃", description = "Redis의 refreshToken을 삭제하고 accessToken, refreshToken 쿠키를 만료 처리한다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.getUserId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expireCookie(ACCESS_TOKEN).toString())
                .header(HttpHeaders.SET_COOKIE, expireCookie(REFRESH_TOKEN).toString())
                .body(ApiResult.success("로그아웃되었습니다.", null));
    }

    @Operation(summary = "토큰 갱신", description = "refreshToken 쿠키로 새 accessToken, refreshToken을 HttpOnly 쿠키로 재발급한다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<Void>> refresh(HttpServletRequest request) {
        String refreshToken = extractCookie(request, REFRESH_TOKEN);
        TokenRefreshResponse response = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_TOKEN, response.getAccessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_TOKEN, response.getRefreshToken()).toString())
                .body(ApiResult.success("토큰이 갱신되었습니다.", null));
    }

    @Operation(summary = "아이디 찾기", description = "이름과 전화번호로 가입 이메일을 찾고 마스킹된 이메일을 반환한다.")
    @PostMapping("/find-email")
    public ResponseEntity<ApiResult<FindEmailResponse>> findEmail(@Valid @RequestBody FindEmailRequest request) {
        FindEmailResponse response = authService.findEmail(request);
        return ResponseEntity.ok(ApiResult.success("가입 이메일을 확인했습니다.", response));
    }

    @Operation(summary = "비밀번호 재설정 요청", description = "가입 이메일로 비밀번호 재설정 링크를 발송한다.")
    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResult<PasswordResetRequestResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        PasswordResetRequestResponse response = authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResult.success("비밀번호 재설정 요청을 접수했습니다.", response));
    }

    @Operation(summary = "비밀번호 재설정 확정", description = "재설정 토큰으로 새 비밀번호를 저장한다.")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResult<PasswordResetConfirmResponse>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        PasswordResetConfirmResponse response = authService.confirmPasswordReset(request);
        return ResponseEntity.ok(ApiResult.success("비밀번호가 재설정되었습니다.", response));
    }

    private ResponseCookie buildCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .build();
    }

    private ResponseCookie expireCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
