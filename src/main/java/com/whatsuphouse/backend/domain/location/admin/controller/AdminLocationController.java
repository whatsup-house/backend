package com.whatsuphouse.backend.domain.location.admin.controller;

import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationCreateRequest;
import com.whatsuphouse.backend.domain.location.admin.dto.request.LocationUpdateRequest;
import com.whatsuphouse.backend.domain.location.admin.service.AdminLocationService;
import com.whatsuphouse.backend.domain.location.common.dto.response.LocationDetailResponse;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "장소 관리 (관리자)", description = "관리자 장소 등록 및 수정 API")
@RestController
@RequestMapping("/api/admin/locations")
@RequiredArgsConstructor
public class AdminLocationController {

    private final AdminLocationService adminLocationService;

    @Operation(summary = "장소 등록", description = "관리자 권한이 필요합니다.")
    @PostMapping
    public ResponseEntity<ApiResult<LocationDetailResponse>> createLocation(
            @Valid @RequestBody LocationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success("장소가 등록되었습니다.", adminLocationService.createLocation(request)));
    }

    @Operation(summary = "장소 수정", description = "관리자 권한이 필요합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<LocationDetailResponse>> updateLocation(
            @Parameter(description = "장소 ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(ApiResult.success("장소가 수정되었습니다.", adminLocationService.updateLocation(id, request)));
    }

    @Operation(summary = "장소 삭제", description = "관리자 권한이 필요합니다. Soft delete 처리됩니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<Void>> deleteLocation(
            @Parameter(description = "장소 ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        adminLocationService.deleteLocation(id);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
