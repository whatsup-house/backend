package com.whatsuphouse.backend.domain.application.admin.controller;

import com.whatsuphouse.backend.domain.application.admin.dto.request.AdminApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
        if (gatheringId != null) {
            return ResponseEntity.ok(ApiResult.success(adminApplicationService.getApplicationsByGathering(gatheringId)));
        }
        if (status != null) {
            return ResponseEntity.ok(ApiResult.success(adminApplicationService.getApplicationsByStatus(status)));
        }
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.getAllApplications()));
    }

    @Operation(summary = "신청 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<AdminApplicationResponse>> getApplication(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.getApplication(id)));
    }

    @Operation(summary = "신청 상태 변경")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResult<AdminApplicationResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestBody AdminApplicationStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(adminApplicationService.changeStatus(id, request)));
    }
}
