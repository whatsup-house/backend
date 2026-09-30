package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.request.MatchingRunRequest;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingResultResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingRunResponse;
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

    @Operation(summary = "자동매칭 실행", description = "rule-v2 엔진으로 매칭을 실행해 제안 테이블(PROPOSED)을 만듭니다(재실행 시 잠기지 않은 제안 테이블은 해체). groupSize로 테이블 인원을 고정할 수 있고 미지정 시 4명입니다. 신규 API는 POST /api/admin/dining/sessions/{id}/match-runs.")
    @PostMapping
    public ResponseEntity<ApiResult<MatchingRunResponse>> run(
            @PathVariable UUID gatheringId,
            @RequestBody(required = false) @Valid MatchingRunRequest request) {
        int groupSize = (request != null && request.getGroupSize() != null)
                ? request.getGroupSize() : MatchingService.DEFAULT_GROUP_SIZE;
        return ResponseEntity.ok(ApiResult.success("자동매칭이 완료되었습니다.",
                matchingService.runMatching(gatheringId, groupSize)));
    }

    @Operation(summary = "매칭 결과 조회", description = "게더링의 매칭 그룹/멤버와 미배정 신청자를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResult<MatchingResultResponse>> result(@PathVariable UUID gatheringId) {
        return ResponseEntity.ok(ApiResult.success(matchingService.getMatchingResult(gatheringId)));
    }
}
