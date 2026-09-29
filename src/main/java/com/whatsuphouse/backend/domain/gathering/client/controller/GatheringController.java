package com.whatsuphouse.backend.domain.gathering.client.controller;

import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
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

    @Operation(summary = "모임 목록 조회", description = "모임 종류 단위 목록입니다. 각 종류에는 조건에 맞는 회차 요약(sessions)이 붙습니다. "
            + "필터가 없으면 오늘 이후 회차만, date를 주면 그 날짜 회차만, status를 주면 그 유효 상태 회차만 봅니다. "
            + "맞는 회차가 없는 종류는 목록에서 빠집니다.")
    @GetMapping
    public ResponseEntity<ApiResult<List<GatheringResponse>>> listGatherings(
            @Parameter(description = "날짜 필터 (YYYY-MM-DD)", example = "2026-04-21")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "회차 상태 필터 (OPEN/CLOSED/DONE/CANCELLED). 날짜가 지난 모집중 회차는 DONE", example = "OPEN")
            @RequestParam(required = false) GatheringSessionStatus status
    ) {
        return ResponseEntity.ok(ApiResult.success(gatheringService.listGatherings(date, status)));
    }

    @Operation(summary = "모임 상세 조회", description = "모임 종류 상세와 전체 회차 목록을 조회합니다. "
            + "옛 회차 ID로 요청하면 그 회차가 속한 종류로 응답합니다. Accept-Language(ko/en/ja)로 번역을 반환합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> getGathering(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        AppLocale locale = AppLocale.fromAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(ApiResult.success(gatheringService.getGathering(id, locale)));
    }

    @Operation(summary = "회차 상세 조회", description = "모임 종류 정보와 해당 회차 1건(sessions)을 조회합니다. Accept-Language(ko/en/ja)로 번역을 반환합니다.")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResult<GatheringDetailResponse>> getSession(
            @PathVariable UUID sessionId,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        AppLocale locale = AppLocale.fromAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(ApiResult.success(gatheringService.getSession(sessionId, locale)));
    }
}
