package com.whatsuphouse.backend.domain.matching.controller;

import com.whatsuphouse.backend.domain.matching.dto.request.ResolutionChooseRequest;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchResolutionResponse;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "우연한 식탁 해결 선택", description = "매칭 실패 시 대체 회차 이동·이용권 보관·환불 선택 API")
@RestController
@RequestMapping("/api/dining/resolutions")
@RequiredArgsConstructor
public class DiningResolutionController {

    private final MatchResolutionService matchResolutionService;

    @Operation(summary = "해결 선택 조회",
            description = "옮길 수 있는 대체 회차, 선택, 응답 기한, 상태. 해결 선택 ID는 내 신청 목록의 resolutionId. 본인 신청이 아니면 403.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<MatchResolutionResponse>> getResolution(
            @Parameter(description = "해결 선택 ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(matchResolutionService.getResolution(id, principal.getUserId())));
    }

    @Operation(summary = "해결 선택",
            description = "TRANSFER: sessionId(제안 회차 중 아직 모집 중인 회차) 필수, 희망 회차를 그 회차로 바꾸고 매칭 대기로 돌아간다(결제 유지). "
                    + "KEEP_TICKET: 이용권을 복원하고 신청을 취소한다. REFUND: 이용권 구매 건을 환불(모의)하고 신청을 취소한다. "
                    + "본인 신청이 아니면 403, 응답 대기(OFFERED)가 아니면 409, sessionId 누락·제안 밖이면 400.")
    @PostMapping("/{id}/choose")
    public ResponseEntity<ApiResult<MatchResolutionResponse>> chooseResolution(
            @Parameter(description = "해결 선택 ID") @PathVariable UUID id,
            @Valid @RequestBody ResolutionChooseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResult.success(
                matchResolutionService.chooseResolution(id, principal.getUserId(), request)));
    }
}
