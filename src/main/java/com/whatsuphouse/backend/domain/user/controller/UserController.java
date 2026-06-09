package com.whatsuphouse.backend.domain.user.controller;

import com.whatsuphouse.backend.domain.user.dto.request.ProfileUpdateRequest;
import com.whatsuphouse.backend.domain.user.dto.request.UserWithdrawRequest;
import com.whatsuphouse.backend.domain.user.dto.response.ProfileResponse;
import com.whatsuphouse.backend.domain.user.dto.response.UserWithdrawResponse;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "회원", description = "프로필 조회 및 수정 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final String ACCESS_TOKEN = "accessToken";
    private static final String REFRESH_TOKEN = "refreshToken";

    private final UserService userService;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResult<ProfileResponse>> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        ProfileResponse response = userService.getProfile(principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(response));
    }

    @Operation(summary = "내 프로필 수정")
    @PutMapping("/me")
    public ResponseEntity<ApiResult<ProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        ProfileResponse response = userService.updateProfile(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResult.success("프로필이 수정되었습니다.", response));
    }

    @Operation(summary = "이메일 중복 확인")
    @GetMapping("/check-email")
    public ResponseEntity<ApiResult<Map<String, Boolean>>> checkEmail(@RequestParam String email) {
        boolean available = userService.isEmailAvailable(email);
        String message = available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다.";
        return ResponseEntity.ok(ApiResult.success(message, Map.of("available", available)));
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResult<Map<String, Boolean>>> checkNickname(@RequestParam String nickname) {
        boolean available = userService.isNicknameAvailable(nickname);
        String message = available ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다.";
        return ResponseEntity.ok(ApiResult.success(message, Map.of("available", available)));
    }

    @Operation(summary = "회원탈퇴", description = "비밀번호 재인증 후 users.delete 값을 Y로 변경해 소프트 딜리트 처리한다.")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResult<UserWithdrawResponse>> withdraw(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserWithdrawRequest request) {
        UserWithdrawResponse response = userService.withdraw(principal.getUserId(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expireCookie(ACCESS_TOKEN).toString())
                .header(HttpHeaders.SET_COOKIE, expireCookie(REFRESH_TOKEN).toString())
                .body(ApiResult.success("회원탈퇴가 완료되었습니다.", response));
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
}
