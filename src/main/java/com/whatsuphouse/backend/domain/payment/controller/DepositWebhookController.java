package com.whatsuphouse.backend.domain.payment.controller;

import com.whatsuphouse.backend.domain.payment.dto.request.DepositWebhookRequest;
import com.whatsuphouse.backend.domain.payment.dto.response.DepositNotificationResponse;
import com.whatsuphouse.backend.domain.payment.service.DepositReconciliationService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "입금 알림", description = "은행 입금 알림 웹훅 자동 대사 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class DepositWebhookController {

    private final DepositReconciliationService depositReconciliationService;

    @Operation(summary = "입금 알림 웹훅",
            description = "MacroDroid·아이폰 단축어가 은행 알림 원문을 보낸다. 로그인 불필요, 헤더 X-Deposit-Secret 필수(불일치 401). "
                    + "본문 2KB 초과 413. 같은 원문은 한 번만 처리하고 기존 결과를 200으로 돌려준다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(schema = @Schema(implementation = DepositWebhookRequest.class)))
    @PostMapping("/deposit-webhook")
    public ResponseEntity<ApiResult<DepositNotificationResponse>> receive(
            @Parameter(description = "공유 시크릿(DEPOSIT_WEBHOOK_SECRET)")
            @RequestHeader(value = "X-Deposit-Secret", required = false) String secret,
            HttpServletRequest request
    ) throws IOException {
        // 본문 크기를 제한하며 읽어야 해서 @RequestBody 대신 스트림을 넘긴다.
        return ResponseEntity.ok(ApiResult.success(
                depositReconciliationService.receiveWebhook(secret, request.getInputStream())));
    }
}
