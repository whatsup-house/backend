package com.whatsuphouse.backend.domain.application.client.controller;

import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningPrefillResponse;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "우연한 식탁", description = "우연한 식탁 참가자 신청 조회·프리필·사전 취소 API")
@RestController
@RequestMapping("/api/dining")
@RequiredArgsConstructor
public class DiningApplicationController {

    private final DiningApplicationService diningApplicationService;

    @Operation(summary = "내 우연한 식탁 신청 목록",
            description = "희망 회차, 배정 회차, 신청·매칭·이용권 상태, 배정 테이블 요약을 함께 반환합니다. 취소한 신청은 빠집니다.")
    @GetMapping("/me/applications")
    public ResponseEntity<ApiResult<DiningApplicationListResponse>> listMyApplications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(diningApplicationService.listMyApplications(principal.getUserId())));
    }

    @Operation(summary = "신청 폼 프리필",
            description = "이 모임 폼의 표준 질문(reservedKey)마다 내 가장 최근 답변을 반환합니다. 답한 적 없는 질문은 빠집니다.")
    @GetMapping("/prefill")
    public ResponseEntity<ApiResult<DiningPrefillResponse>> getPrefill(
            @Parameter(description = "모임 종류 ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @RequestParam UUID gatheringId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(diningApplicationService.getPrefill(principal.getUserId(), gatheringId)));
    }

    @Operation(summary = "우연한 식탁 신청 사전 취소",
            description = "회차 시작 2일 전까지 취소할 수 있습니다(배정 전이면 아직 시작하지 않은 희망 회차 중 가장 이른 회차 기준). "
                    + "차감한 이용권은 복원됩니다. 기한이 지나면 CANCEL_WINDOW_CLOSED, 이미 취소한 신청은 409입니다.")
    @PostMapping("/applications/{id}/cancel")
    public ResponseEntity<ApiResult<Void>> cancel(
            @Parameter(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        diningApplicationService.cancelApplication(id, principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
