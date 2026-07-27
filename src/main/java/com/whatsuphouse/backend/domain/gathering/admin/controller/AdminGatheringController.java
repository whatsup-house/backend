package com.whatsuphouse.backend.domain.gathering.admin.controller;

import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.service.AdminGatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "모임 관리 (관리자)", description = "관리자 모임 생성, 수정, 상태 변경 API")
@RestController
@RequestMapping("/api/admin/gatherings")
@RequiredArgsConstructor
public class AdminGatheringController {

    private final AdminGatheringService adminGatheringService;

    @Operation(summary = "모임 생성", description = "관리자 권한이 필요합니다.")
    @PostMapping
    public ResponseEntity<ApiResult<GatheringDetailResponse>> createGathering(
            @Valid @RequestBody GatheringCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("모임이 등록되었습니다.", adminGatheringService.createGathering(request)));
    }

    @Operation(summary = "모임 수정", description = "관리자 권한이 필요합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> updateGathering(
            @PathVariable UUID id,
            @Valid @RequestBody GatheringUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("모임이 수정되었습니다.", adminGatheringService.updateGathering(id, request)));
    }

    @Operation(summary = "모임 상태 변경", description = "관리자 권한이 필요합니다. (OPEN/CLOSED/COMPLETED/CANCELLED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResult<Void>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody GatheringStatusRequest request
    ) {
        adminGatheringService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResult.success("모임 상태가 변경되었습니다.", null));
    }
}
