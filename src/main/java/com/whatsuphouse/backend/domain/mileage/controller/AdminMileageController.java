package com.whatsuphouse.backend.domain.mileage.controller;

import com.whatsuphouse.backend.domain.mileage.dto.request.AdminMileageRequest;
import com.whatsuphouse.backend.domain.mileage.dto.response.MileageHistoryResponse;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "마일리지 관리 (관리자)", description = "관리자 마일리지 부여/차감 API")
@RestController
@RequestMapping("/api/admin/mileage")
@RequiredArgsConstructor
public class AdminMileageController {

    private final MileageService mileageService;

    @Operation(summary = "마일리지 부여/차감", description = "관리자 권한이 필요합니다. amount 양수=부여, 음수=차감")
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResult<MileageHistoryResponse>> adjustMileage(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminMileageRequest request
    ) {
        MileageHistoryResponse response = mileageService.adminAdjust(userId, request.getAmount(), request.getAdjustReason());
        return ResponseEntity.ok(ApiResult.success("마일리지가 조정되었습니다.", response));
    }
}
