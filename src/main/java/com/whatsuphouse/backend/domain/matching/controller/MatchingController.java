package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.request.MatchingRunRequest;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingResultResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingRunResponse;
import com.whatsuphouse.backend.domain.matching.service.MatchingEngine;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "자동매칭 (관리자)", description = "우연한 식탁 자동매칭 실행/조회 API")
@RestController
@RequestMapping("/api/admin/gatherings/{gatheringId}/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @Operation(summary = "자동매칭 실행", description = "CONFIRMED 신청을 대상으로 추천 그룹(PENDING)을 생성합니다. groupSize로 그룹당 인원 수를 지정할 수 있고 미지정 시 기본 4명입니다.")
    @PostMapping
    public ResponseEntity<ApiResult<MatchingRunResponse>> run(
            @PathVariable UUID gatheringId,
            @RequestBody(required = false) @Valid MatchingRunRequest request) {
        int groupSize = (request != null && request.getGroupSize() != null)
                ? request.getGroupSize() : MatchingEngine.DEFAULT_GROUP_SIZE;
        return ResponseEntity.ok(ApiResult.success("자동매칭이 완료되었습니다.",
                matchingService.runMatching(gatheringId, groupSize)));
    }

    @Operation(summary = "매칭 결과 조회", description = "게더링의 매칭 그룹/멤버와 미배정 신청자를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<MatchingResultResponse>> result(@PathVariable UUID gatheringId) {
        return ResponseEntity.ok(ApiResult.success(matchingService.getMatchingResult(gatheringId)));
    }
}
