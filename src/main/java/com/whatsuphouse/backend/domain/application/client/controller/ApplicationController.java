package com.whatsuphouse.backend.domain.application.client.controller;

import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationCheckResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.ApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.request.ApplicationCreateRequest;
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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Tag(name = "신청", description = "게더링 신청 API")
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "모임 신청 (회원)", description = "모임 종류 ID와 희망 회차 ID 목록으로 신청합니다. "
            + "일반 모임은 회차를 정확히 1개 골라야 하며 그 회차로 바로 배정됩니다. "
            + "우연한 식탁은 1개 이상 고르며(앞일수록 우선) 이용권이 차감되면 확정(CONFIRMED)·매칭 대기(WAITING), "
            + "이용권이 없으면 결제 대기(PAYMENT_PENDING)로 접수됩니다. 다른 종류의 회차가 섞이면 400입니다. "
            + "마감이 지난 회차는 신청할 수 없습니다.")
    @PostMapping("/api/applications")
    public ResponseEntity<ApiResult<ApplicationResponse>> create(
            @Valid @RequestBody ApplicationCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.apply(request, principal.getUserId())));
    }

    @Operation(summary = "게더링 신청 (회원, 기존 경로)", description = "경로의 gatheringId는 회차 ID입니다. POST /api/applications로 대체됩니다.")
    @PostMapping("/api/gatherings/{gatheringId}/applications")
    public ResponseEntity<ApiResult<ApplicationResponse>> apply(
            @PathVariable UUID gatheringId,
            @Valid @RequestBody ApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.apply(gatheringId, request, principal.getUserId())));
    }

    @Operation(summary = "게더링 신청 (비회원)", description = "비회원이 일반 모임 회차에 신청합니다. 경로의 gatheringId는 회차 ID입니다.")
    @PostMapping("/api/gatherings/{gatheringId}/applications/guest")
    public ResponseEntity<ApiResult<ApplicationResponse>> applyAsGuest(
            @PathVariable UUID gatheringId,
            @Valid @RequestBody ApplicationRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.applyAsGuest(gatheringId, request)));
    }

    @Operation(summary = "신청 조회 (비회원)", description = "전화번호와 예약번호 또는 메일 링크 토큰으로 신청 내역을 조회합니다.")
    @GetMapping("/api/applications/check")
    public ResponseEntity<ApiResult<ApplicationCheckResponse>> checkApplication(
            @Parameter(description = "전화번호", example = "01012345678") @RequestParam(required = false) String phone,
            @Parameter(description = "예약번호", example = "WH260415-A1B2C3") @RequestParam(required = false) String bookingNumber,
            @Parameter(description = "메일 링크 조회 토큰") @RequestParam(required = false) String token
    ) {
        if (StringUtils.hasText(token)) {
            return ResponseEntity.ok(ApiResult.success(applicationService.checkApplicationByToken(token)));
        }
        return ResponseEntity.ok(ApiResult.success(applicationService.checkApplication(phone, bookingNumber)));
    }

    @Operation(summary = "내 신청 목록 조회 (회원)", description = "로그인된 회원의 신청 목록을 조회합니다.")
    @GetMapping("/api/applications")
    public ResponseEntity<ApiResult<List<ApplicationListResponse>>> getMyApplications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.getMyApplications(principal.getUserId())));
    }

    @Operation(summary = "내 신청 상세 조회 (회원)", description = "로그인된 회원이 자신의 신청 내역과 작성한 답변을 조회합니다.")
    @GetMapping("/api/applications/{id}")
    public ResponseEntity<ApiResult<ApplicationCheckResponse>> getMyApplication(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(applicationService.getMyApplication(id, principal.getUserId())));
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
