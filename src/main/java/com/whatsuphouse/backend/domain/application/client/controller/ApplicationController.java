package com.whatsuphouse.backend.domain.application.client.controller;

import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationResponse;
import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "신청", description = "게더링 신청 API")
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "게더링 신청 (회원)", description = "로그인된 회원이 게더링에 신청합니다.")
    @PostMapping("/api/gatherings/{gatheringId}/applications")
    public ResponseEntity<ApiResult<ApplicationResponse>> apply(
            @PathVariable UUID gatheringId,
            @Valid @RequestBody ApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.apply(gatheringId, request, principal.getUserId())));
    }

    @Operation(summary = "게더링 신청 (비회원)", description = "비회원이 게더링에 신청합니다.")
    @PostMapping("/api/gatherings/{gatheringId}/applications/guest")
    public ResponseEntity<ApiResult<ApplicationResponse>> applyAsGuest(
            @PathVariable UUID gatheringId,
            @Valid @RequestBody ApplicationRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.applyAsGuest(gatheringId, request)));
    }

    @Operation(summary = "신청 조회 (비회원)", description = "전화번호와 예약번호로 신청 내역을 조회합니다.")
    @GetMapping("/api/applications/check")
    public ResponseEntity<ApiResult<ApplicationCheckResponse>> checkApplication(
            @Parameter(description = "전화번호", example = "01012345678") @RequestParam String phone,
            @Parameter(description = "예약번호", example = "WH260415-A1B2C3") @RequestParam String bookingNumber
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.checkApplication(phone, bookingNumber)));
    }

    @Operation(summary = "내 신청 목록 조회 (회원)", description = "로그인된 회원의 신청 목록을 조회합니다.")
    @GetMapping("/api/applications")
    public ResponseEntity<ApiResult<List<ApplicationListResponse>>> getMyApplications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.getMyApplications(principal.getUserId())));
    }

    @Operation(summary = "신청 취소 (회원)", description = "로그인된 회원이 자신의 신청을 취소합니다.")
    @DeleteMapping("/api/applications/{id}")
    public ResponseEntity<ApiResult<Void>> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        applicationService.cancel(id, principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
