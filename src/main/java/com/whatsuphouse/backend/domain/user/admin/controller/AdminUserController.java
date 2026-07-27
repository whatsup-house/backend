package com.whatsuphouse.backend.domain.user.admin.controller;

import com.whatsuphouse.backend.domain.user.admin.dto.request.UserStatusRequest;
import com.whatsuphouse.backend.domain.user.admin.dto.response.UserDetailResponse;
import com.whatsuphouse.backend.domain.user.admin.dto.response.UserPageResponse;
import com.whatsuphouse.backend.domain.user.admin.service.AdminUserService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "관리자 - 회원", description = "회원 관리 API")
@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "회원 목록 조회", description = "전체 회원 목록을 페이지네이션으로 조회한다. 닉네임 또는 이메일 부분 일치 검색을 지원한다.")
    @GetMapping
    public ResponseEntity<ApiResult<UserPageResponse>> listUsers(
            @Parameter(description = "닉네임 또는 이메일 검색어", example = "홍길동")
            @RequestParam(required = false) String search,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @Min(0) @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (기본값 20, 최대 100)", example = "20")
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(adminUserService.listUsers(search, page, size)));
    }

    @Operation(summary = "회원 상세 조회", description = "회원의 프로필, 마일리지, 신청 이력, 계정 상태를 조회한다. (KAN-188)")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResult<UserDetailResponse>> getUser(
            @Parameter(description = "회원 ID") @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResult.success(adminUserService.getUser(userId)));
    }

    @Operation(summary = "회원 상태 변경", description = "회원 계정을 정지(SUSPENDED)하거나 해제(ACTIVE)한다. (KAN-188)")
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResult<Void>> changeStatus(
            @Parameter(description = "회원 ID") @PathVariable UUID userId,
            @Valid @RequestBody UserStatusRequest request
    ) {
        adminUserService.changeStatus(userId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
