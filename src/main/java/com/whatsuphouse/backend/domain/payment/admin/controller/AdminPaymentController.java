package com.whatsuphouse.backend.domain.payment.admin.controller;

import com.whatsuphouse.backend.domain.payment.dto.PendingPayment;
import com.whatsuphouse.backend.domain.payment.dto.request.DepositNotificationLinkRequest;
import com.whatsuphouse.backend.domain.payment.dto.request.PayoutSentRequest;
import com.whatsuphouse.backend.domain.payment.dto.response.AdminDepositNotificationPageResponse;
import com.whatsuphouse.backend.domain.payment.dto.response.AdminDepositNotificationResponse;
import com.whatsuphouse.backend.domain.payment.dto.response.AdminPayoutResponse;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.domain.payment.service.DepositReconciliationService;
import com.whatsuphouse.backend.domain.payment.service.PayoutService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "관리자 결제", description = "입금 알림 로그·결제 대기 건 수동 연결·송금 목록(환불·보증금 반환) API")
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final DepositReconciliationService depositReconciliationService;
    private final PayoutService payoutService;

    @Operation(summary = "결제 대기 후보 목록",
            description = "자동 대사와 같은 후보(1회권+같은 신청 보증금은 합산 1건). 입금 알림 수동 연결 드롭다운용.")
    @GetMapping("/pending-targets")
    public ResponseEntity<ApiResult<List<PendingPayment>>> pendingTargets() {
        return ResponseEntity.ok(ApiResult.success(depositReconciliationService.listPendingTargets()));
    }

    @Operation(summary = "입금 알림 로그(최신순)", description = "result를 비우면 전체.")
    @GetMapping("/deposit-notifications")
    public ResponseEntity<ApiResult<AdminDepositNotificationPageResponse>> depositNotifications(
            @Parameter(description = "대사 결과 필터", example = "NO_CANDIDATE") @RequestParam(required = false) DepositMatchResult result,
            @Parameter(description = "페이지(0부터)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResult.success(depositReconciliationService.listNotifications(result, page, size)));
    }

    @Operation(summary = "입금 알림 수동 연결",
            description = "예외 건 알림을 결제 대기 건(pending-targets)에 연결해 자동 대사와 같은 결제 완료 처리 후 예외 건을 닫는다.")
    @PostMapping("/deposit-notifications/{id}/link")
    public ResponseEntity<ApiResult<AdminDepositNotificationResponse>> link(
            @Parameter(description = "입금 알림 ID") @PathVariable UUID id,
            @Valid @RequestBody DepositNotificationLinkRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResult.success(
                depositReconciliationService.linkNotification(id, request, principal.getUserId())));
    }

    @Operation(summary = "송금 목록", description = "운영자가 직접 이체할 환불(REFUND)·보증금 반환(DEPOSIT_RETURN). 오래된 요청부터. "
            + "송금 30일이 지난 건은 계좌번호 끝 4자리만 보인다.")
    @GetMapping("/payouts")
    public ResponseEntity<ApiResult<List<AdminPayoutResponse>>> payouts(
            @Parameter(description = "송금 종류", example = "REFUND") @RequestParam PayoutType type,
            @Parameter(description = "송금 상태", example = "REQUESTED") @RequestParam PayoutStatus status
    ) {
        return ResponseEntity.ok(ApiResult.success(payoutService.list(type, status)));
    }

    @Operation(summary = "송금 완료 처리",
            description = "이체를 끝낸 송금 요청을 SENT로. 환불이면 이용권 차감 상태 REFUNDED, 보증금 반환이면 보증금 RETURNED. "
                    + "이미 SENT인 건은 무시, 없는 ID면 404(전체 취소).")
    @PostMapping("/payouts/sent")
    public ResponseEntity<ApiResult<Void>> markPayoutsSent(
            @Valid @RequestBody PayoutSentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        payoutService.markSent(request.ids(), principal.getUserId());
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
