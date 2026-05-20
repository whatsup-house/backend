package com.whatsuphouse.backend.domain.home.controller;

import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "홈 화면 (모임)", description = "홈 화면 큐레이션 게더링 목록 조회 API")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeGatheringController {

    private final GatheringService gatheringService;

    @Operation(summary = "큐레이션 게더링 목록 조회", description = "홈 화면에 노출할 큐레이션 게더링 목록을 순위 순으로 반환합니다. 인증 불필요.")
    @GetMapping("/curated")
    public ResponseEntity<ApiResult<List<CuratedGatheringResponse>>> listCuratedGatherings() {
        return ResponseEntity.ok(ApiResult.success(gatheringService.listCuratedGatherings()));
    }
}
