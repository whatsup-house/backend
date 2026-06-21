package com.whatsuphouse.backend.domain.gathering.client.controller;

import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.global.common.ApiResult;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "모임", description = "모임 조회 API")
@RestController
@RequestMapping("/api/gatherings")
@RequiredArgsConstructor
public class GatheringController {

    private final GatheringService gatheringService;

    @Operation(summary = "모임 목록 조회", description = "날짜 및 상태로 필터링하여 모임 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<List<GatheringResponse>>> listGatherings(
            @Parameter(description = "날짜 필터 (YYYY-MM-DD)", example = "2026-04-21")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "상태 필터", example = "OPEN")
            @RequestParam(required = false) GatheringStatus status
    ) {
        return ResponseEntity.ok(ApiResult.success(gatheringService.listGatherings(date, status)));
    }

    @Operation(summary = "모임 상세 조회", description = "모임 상세 정보를 조회합니다. 장소 정보(주소, 지도 URL)를 포함합니다. Accept-Language(ko/en/ja)로 번역을 반환합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> getGathering(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        AppLocale locale = AppLocale.fromAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(ApiResult.success(gatheringService.getGathering(id, locale)));
    }
}
