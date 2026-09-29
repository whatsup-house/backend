package com.whatsuphouse.backend.domain.gathering.admin.controller;

import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationOrderRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringCurationRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionCreateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringSessionRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringStatusRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.request.GatheringUpdateRequest;
import com.whatsuphouse.backend.domain.gathering.admin.dto.response.AdminGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.admin.service.AdminGatheringService;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringSessionResponse;
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

    @Operation(summary = "모임 회차 목록 조회", description = "관리자 권한이 필요합니다. 항목 하나가 회차 하나이며 종류 ID(gatheringId)를 포함합니다. "
            + "status/eventDate/from/to 필터 지원. eventDate와 from/to 동시 요청 시 eventDate 우선.")
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminGatheringResponse>>> listGatherings(
            @Parameter(description = "게더링 상태", example = "OPEN") @RequestParam(required = false) GatheringStatus status,
            @Parameter(description = "특정 날짜 (YYYY-MM-DD)", example = "2026-05-10") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @Parameter(description = "시작 날짜 (YYYY-MM-DD)", example = "2026-05-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료 날짜 (YYYY-MM-DD)", example = "2026-05-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResult.success(adminGatheringService.listGatherings(status, eventDate, from, to)));
    }

    @Operation(summary = "모임 종류 상세 조회 (관리자)", description = "수정 패널 prefill용. 종류 필드와 전체 회차 목록을 반환합니다. 회차 ID로 요청하면 그 회차의 종류로 응답합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> getGathering(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResult.success(adminGatheringService.getGathering(id)));
    }

    @Operation(summary = "모임 종류 생성", description = "관리자 권한이 필요합니다. 회차는 POST /{id}/sessions로 추가합니다.")
    @PostMapping
    public ResponseEntity<ApiResult<GatheringDetailResponse>> createGathering(
            @Valid @RequestBody GatheringCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("모임이 등록되었습니다.", adminGatheringService.createGathering(request)));
    }

    @Operation(summary = "모임 종류 수정", description = "관리자 권한이 필요합니다. 종류 ID만 받습니다. 타입(gatheringType)은 바꿀 수 없습니다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> updateGathering(
            @PathVariable UUID id,
            @Valid @RequestBody GatheringUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("모임이 수정되었습니다.", adminGatheringService.updateGathering(id, request)));
    }

    @Operation(summary = "모임 종류 삭제", description = "관리자 권한이 필요합니다. 회차도 함께 삭제합니다. 신청이 있는 회차가 있으면 409.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> deleteGathering(@PathVariable UUID id) {
        adminGatheringService.deleteGathering(id);
        return ResponseEntity.ok(ApiResult.success("모임이 삭제되었습니다.", null));
    }

    @Operation(summary = "회차 생성", description = "관리자 권한이 필요합니다. 단건은 회차 필드를 최상위에, "
            + "주간 반복은 {base: 회차 필드, repeatWeekly: {until}}로 보냅니다. 반복은 기준 날짜부터 until까지 7일 간격(1년 이내)이며 신청 마감도 같은 간격으로 밀립니다.")
    @PostMapping("/{id}/sessions")
    public ResponseEntity<ApiResult<List<GatheringSessionResponse>>> createSessions(
            @PathVariable UUID id,
            @Valid @RequestBody GatheringSessionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("회차가 등록되었습니다.", adminGatheringService.createSessions(id, request)));
    }

    @Operation(summary = "회차 수정", description = "관리자 권한이 필요합니다. 상태 변경은 PATCH /{회차 ID}/status를 씁니다.")
    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResult<GatheringSessionResponse>> updateSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody GatheringSessionRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success("회차가 수정되었습니다.", adminGatheringService.updateSession(sessionId, request)));
    }

    @Operation(summary = "회차 삭제", description = "관리자 권한이 필요합니다. 이 회차에 배정됐거나 희망 회차로 고른 신청이 있으면 409.")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResult<Void>> deleteSession(@PathVariable UUID sessionId) {
        adminGatheringService.deleteSession(sessionId);
        return ResponseEntity.ok(ApiResult.success("회차가 삭제되었습니다.", null));
    }

    @Operation(summary = "회차 상태 변경", description = "관리자 권한이 필요합니다. 경로 id는 회차 ID입니다. (OPEN/CLOSED/COMPLETED/CANCELLED)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResult<Void>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody GatheringStatusRequest request
    ) {
        adminGatheringService.changeStatus(id, request);
        return ResponseEntity.ok(ApiResult.success("모임 상태가 변경되었습니다.", null));
    }

    @Operation(summary = "큐레이션 토글", description = "관리자 권한이 필요합니다. 종류의 큐레이션 노출 여부를 설정합니다. 종류 ID 또는 회차 ID를 받습니다.")
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
