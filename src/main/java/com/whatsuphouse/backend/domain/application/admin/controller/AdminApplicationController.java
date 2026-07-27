package com.whatsuphouse.backend.domain.application.admin.controller;

import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationPaymentRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationDeleteResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationPaymentResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusResponse;
import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "관리자 - 신청", description = "신청 관리 API")
@RestController
@RequestMapping("/api/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final AdminApplicationService adminApplicationService;

    @Operation(summary = "전체 신청 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminApplicationResponse>>> getAllApplications(
            @Parameter(description = "게더링 ID로 필터링") @RequestParam(required = false) UUID gatheringId,
            @Parameter(description = "상태로 필터링") @RequestParam(required = false) ApplicationStatus status
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.getAllApplications(gatheringId, status)));
    }

    @Operation(summary = "신청 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<AdminApplicationResponse>> getApplication(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.getApplication(id)));
    }

    @Operation(summary = "신청 삭제 (소프트 삭제)", description = "이미 CANCELLED인 경우 멱등 처리(200 OK). ATTENDED 상태는 삭제 불가.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<ApplicationDeleteResponse>> deleteApplication(
            @Parameter(description = "신청 ID") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.deleteApplication(id)));
    }

    @Operation(summary = "신청 상태 변경")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResult<ApplicationStatusResponse>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.changeStatus(id, request)));
    }

    @Operation(summary = "입금 확인 토글", description = "유료 게더링 신청의 입금 확인/해제. 신청 상태와 독립적으로 동작한다.")
    @PatchMapping("/{id}/payment")
    public ResponseEntity<ApiResult<ApplicationPaymentResponse>> changePayment(
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationPaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.changePayment(id, request)));
    }
}
