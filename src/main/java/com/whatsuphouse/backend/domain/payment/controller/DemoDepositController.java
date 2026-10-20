package com.whatsuphouse.backend.domain.payment.controller;

import com.whatsuphouse.backend.domain.payment.dto.request.DemoDepositRequest;
import com.whatsuphouse.backend.domain.payment.dto.response.DepositNotificationResponse;
import com.whatsuphouse.backend.domain.payment.service.DepositReconciliationService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "심사용 입금 시뮬레이터", description = "데모 회원 전용 입금 체험 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class DemoDepositController {

    private final DepositReconciliationService depositReconciliationService;

    @Operation(summary = "심사용 입금 시뮬레이터",
            description = "데모 회원만(그 외 403). passId(이용권, 같은 신청 보증금 합산) 또는 applicationId(보증금만) 중 정확히 하나(아니면 400). "
                    + "본인 결제 대기 건의 입금자명·금액으로 은행 푸시 형식 원문을 만들어 웹훅과 같은 자동 대사를 태우고 결과를 돌려준다.")
    @PostMapping("/demo-deposit")
    public ResponseEntity<ApiResult<DepositNotificationResponse>> demoDeposit(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DemoDepositRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(
                depositReconciliationService.simulateDeposit(principal.getUserId(), request)));
    }
}
