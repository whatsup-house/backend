package com.whatsuphouse.backend.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입금 알림 웹훅 본문(MacroDroid·아이폰 단축어). 본문 크기 제한 때문에 서비스가 직접 읽어 검증한다.
 * source·receivedAt은 폰 자동화에서 형식이 흔들려도 입금을 놓치지 않도록 문자열로 받아 관대하게 해석한다.
 */
@Getter
@NoArgsConstructor
public class DepositWebhookRequest {

    @NotBlank
    @Schema(description = "알림 원문", example = "[입금] 한지형        40,000원 1002-157-849***계좌 잔액 17,163,887원 09/18 13:19:30")
    private String text;

    @Schema(description = "출처(MACRODROID|IOS_SHORTCUT). 없거나 모르는 값이면 MACRODROID", example = "MACRODROID")
    private String source;

    @Schema(description = "폰 수신 시각(ISO-8601). 없거나 못 읽으면 서버 수신 시각", example = "2026-10-20T13:19:30")
    private String receivedAt;
}
