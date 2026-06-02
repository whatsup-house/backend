package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.response.MatchingRunResponse;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "자동매칭 실행", description = "CONFIRMED 신청을 대상으로 추천 그룹(PENDING)을 생성합니다. 기존 추천은 재생성됩니다.")
    @PostMapping
    public ResponseEntity<ApiResult<MatchingRunResponse>> run(@PathVariable UUID gatheringId) {
        return ResponseEntity.ok(ApiResult.success("자동매칭이 완료되었습니다.", matchingService.runMatching(gatheringId)));
    }
}
