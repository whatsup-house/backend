package com.whatsuphouse.backend.domain.application.client.controller;

import com.whatsuphouse.backend.domain.application.client.dto.request.DiningCancelRequest;
import com.whatsuphouse.backend.domain.application.client.dto.request.DiningWishUpdateRequest;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningApplicationListResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningCancelPreviewResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.DiningPrefillResponse;
import com.whatsuphouse.backend.domain.application.client.service.DiningApplicationService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            description = "희망 회차, 배정 회차, 신청·매칭·이용권·보증금 상태, 배정 테이블 요약을 함께 반환합니다. "
                    + "최근 30일 안에 취소한 신청은 status=CANCELLED로 포함합니다.")
    @GetMapping("/me/applications")
    public ResponseEntity<ApiResult<DiningApplicationListResponse>> listMyApplications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(diningApplicationService.listMyApplications(principal.getUserId())));
    }

    @Operation(summary = "매칭 대기 중 일정·지역 바꾸기",
            description = "매칭 대기(WAITING·REALLOCATING)인 내 신청의 희망 회차(우선순위 순)와 희망 지역(지망 순)을 통째로 바꿉니다. "
                    + "결제·이용권·보증금은 그대로입니다. 회차는 같은 모임의 모집 중·마감 전·매칭 실행 전 회차만 고를 수 있습니다. "
                    + "본인 신청이 아니면 403, 대기 상태가 아니거나 매칭을 이미 실행한 회차면 WISH_CHANGE_NOT_ALLOWED(409), "
                    + "없는 회차 SESSION_NOT_FOUND, 다른 모임 회차 SESSION_GATHERING_MISMATCH, 모집 마감 GATHERING_NOT_RECRUITING·APPLY_DEADLINE_PASSED, "
                    + "지역 누락 REGION_REQUIRED, 모르는 지역 INVALID_REGION, 그 회차에 내 다른 신청이 있으면 ALREADY_APPLIED.")
    @PutMapping("/me/applications/{id}/wishes")
    public ResponseEntity<ApiResult<Void>> updateWishes(
            @Parameter(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody DiningWishUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        diningApplicationService.updateWishes(id, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResult.success(null));
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

    @Operation(summary = "우연한 식탁 취소 미리보기",
            description = "지금 취소하면 적용되는 결과(시점, 이용권 환불 금액, 보증금 반환 금액·처리, 환불 계좌 필요 여부, 제재)를 반환합니다. "
                    + "취소와 같은 계산·오류 규칙을 따릅니다.")
    @GetMapping("/applications/{id}/cancel-preview")
    public ResponseEntity<ApiResult<DiningCancelPreviewResponse>> cancelPreview(
            @Parameter(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(diningApplicationService.previewCancel(id, principal.getUserId())));
    }

    @Operation(summary = "우연한 식탁 신청 취소",
            description = "기준 회차(배정 회차, 배정 전이면 아직 시작하지 않은 희망 회차 중 가장 이른 회차) 날짜로 시점을 나눕니다. "
                    + "마감(전날 0시) 전: 1회권 1회분 전액 환불·보증금 전액 반환. 마감 후: 1회권 50% 환불·보증금 50% 반환. "
                    + "당일(테이블에 앉은 신청): 환불 없음·보증금 몰수·보증금 대상 지정. 이용권은 복원하지 않습니다. "
                    + "이용권 환불이 있으면 refundAccount 필수(없으면 REFUND_ACCOUNT_REQUIRED), 보증금은 신청 때 받은 계좌로 반환합니다. "
                    + "회차가 시작했으면 CANCEL_WINDOW_CLOSED, 이미 취소한 신청은 409입니다.")
    @PostMapping("/applications/{id}/cancel")
    public ResponseEntity<ApiResult<Void>> cancel(
            @Parameter(description = "신청 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) DiningCancelRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        diningApplicationService.cancelApplication(id, principal.getUserId(),
                request != null ? request.getRefundAccount() : null);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
