package com.whatsuphouse.backend.domain.gathering.admin.controller;

import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationOrderRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.response.AdminGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.admin.service.AdminGatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "모임 관리 (관리자)", description = "관리자 모임 목록 조회, 생성, 수정, 상태 변경 API")
@RestController
@RequestMapping("/api/admin/gatherings")
@RequiredArgsConstructor
public class AdminGatheringController {

    private final AdminGatheringService adminGatheringService;

    @Operation(summary = "모임 목록 조회", description = "관리자 권한이 필요합니다. status/eventDate/from/to 필터 지원. eventDate와 from/to 동시 요청 시 eventDate 우선.")
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminGatheringResponse>>> listGatherings(
            @Parameter(description = "게더링 상태", example = "OPEN") @RequestParam(required = false) GatheringStatus status,
            @Parameter(description = "특정 날짜 (YYYY-MM-DD)", example = "2026-05-10") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @Parameter(description = "시작 날짜 (YYYY-MM-DD)", example = "2026-05-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료 날짜 (YYYY-MM-DD)", example = "2026-05-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResult.success(adminGatheringService.listGatherings(status, eventDate, from, to)));
    }

    @Operation(summary = "모임 상세 조회 (관리자)", description = "수정 패널 prefill용. 소개/장소/시간/썸네일 등 전체 필드를 반환합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> getGathering(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.success(adminGatheringService.getGathering(id)));
    }

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

    @Operation(summary = "큐레이션 토글", description = "관리자 권한이 필요합니다. 게더링 큐레이션 노출 여부를 설정합니다.")
    @PatchMapping("/{id}/curation")
    public ResponseEntity<ApiResult<Void>> toggleCuration(
            @PathVariable UUID id,
            @Valid @RequestBody GatheringCurationRequest request
    ) {
        adminGatheringService.toggleCuration(id, request);
        return ResponseEntity.ok(ApiResult.success("큐레이션 설정이 변경되었습니다.", null));
    }

    @Operation(summary = "큐레이션 순서 변경", description = "관리자 권한이 필요합니다. gatheringIds 순서대로 노출 순위를 설정합니다.")
    @PutMapping("/curated/order")
    public ResponseEntity<ApiResult<Void>> reorderCurated(
            @Valid @RequestBody GatheringCurationOrderRequest request
    ) {
        adminGatheringService.reorderCurated(request);
        return ResponseEntity.ok(ApiResult.success("큐레이션 순서가 변경되었습니다.", null));
    }
}
