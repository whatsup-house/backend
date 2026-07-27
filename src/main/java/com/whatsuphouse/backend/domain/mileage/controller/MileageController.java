package com.whatsuphouse.backend.domain.mileage.controller;

import com.whatsuphouse.backend.domain.mileage.dto.response.MileageBalanceResponse;
import com.whatsuphouse.backend.domain.mileage.dto.response.MileageHistoryPageResponse;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마일리지", description = "마일리지 조회 API")
@RestController
@RequestMapping("/api/mileage")
@RequiredArgsConstructor
public class MileageController {

    private final MileageService mileageService;

    @Operation(summary = "내 마일리지 잔액 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResult<MileageBalanceResponse>> getMyMileage(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(mileageService.getMyMileage(principal.getUserId())));
    }

    @Operation(summary = "내 마일리지 이력 조회")
    @GetMapping("/me/history")
    public ResponseEntity<ApiResult<MileageHistoryPageResponse>> getMyMileageHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "마일리지 타입 필터")
            @RequestParam(required = false) MileageType type,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(
                mileageService.getMyMileageHistory(principal.getUserId(), type, page, size)));
    }
}
