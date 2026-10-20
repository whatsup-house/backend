package com.whatsuphouse.backend.domain.payment.dto.response;

import com.whatsuphouse.backend.domain.payment.entity.DepositNotification;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** 웹훅·심사용 시뮬레이터 처리 결과. 시뮬레이터 카드가 단계별 체크에 쓴다. (KAN-403) */
@Getter
@Builder
public class DepositNotificationResponse {

    @Schema(description = "알림 원문", example = "[입금] 한지형        59,900원 1002-000-000***계좌 잔액 1,234,567원 10/20 13:19:30")
    private String rawText;

    @Schema(description = "파싱한 입금자명. 입금 알림이 아니거나 파싱 실패면 null", example = "한지형")
    private String parsedName;

    @Schema(description = "파싱한 금액. 입금 알림이 아니거나 파싱 실패면 null", example = "59900")
    private Integer parsedAmount;

    @Schema(description = "연결된 신청 ID. MATCHED이고 신청이 있는 결제일 때만", example = "b3f1c2d4-...")
    private UUID linkedApplicationId;

    @Schema(description = "대사 결과", example = "MATCHED")
    private DepositMatchResult result;

    public static DepositNotificationResponse from(DepositNotification notification) {
        return DepositNotificationResponse.builder()
                .rawText(notification.getRawText())
                .parsedName(notification.getParsedName())
                .parsedAmount(notification.getParsedAmount())
                .linkedApplicationId(notification.getResult() == DepositMatchResult.MATCHED
                        ? notification.getApplicationId() : null)
                .result(notification.getResult())
                .build();
    }
}
