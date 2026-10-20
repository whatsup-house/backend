package com.whatsuphouse.backend.domain.payment.dto.response;

import com.whatsuphouse.backend.domain.payment.entity.DepositNotification;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import com.whatsuphouse.backend.domain.payment.enums.DepositSource;
import com.whatsuphouse.backend.domain.payment.enums.PaymentTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** 운영자 입금 알림 로그 행. (KAN-403) */
@Getter
@Builder
public class AdminDepositNotificationResponse {

    @Schema(description = "알림 ID")
    private UUID id;
    @Schema(description = "알림 원문")
    private String rawText;
    @Schema(description = "출처", example = "MACRODROID")
    private DepositSource source;
    @Schema(description = "폰이 알림을 받은 시각(없으면 서버 수신 시각)")
    private LocalDateTime receivedAt;
    @Schema(description = "파싱한 입금자명", example = "한지형")
    private String parsedName;
    @Schema(description = "파싱한 금액", example = "59900")
    private Integer parsedAmount;
    @Schema(description = "대사 결과", example = "MATCHED")
    private DepositMatchResult result;
    @Schema(description = "연결된 결제 대상 종류", example = "TICKET_PASS")
    private PaymentTargetType targetType;
    @Schema(description = "연결된 결제 대상 ID(이용권·보증금·신청)")
    private UUID targetId;
    @Schema(description = "연결된 신청 ID")
    private UUID applicationId;
    @Schema(description = "대사 실패로 연 예외함 건 ID")
    private UUID exceptionCaseId;
    @Schema(description = "기록 시각")
    private LocalDateTime createdAt;

    public static AdminDepositNotificationResponse from(DepositNotification notification) {
        return AdminDepositNotificationResponse.builder()
                .id(notification.getId())
                .rawText(notification.getRawText())
                .source(notification.getSource())
                .receivedAt(notification.getReceivedAt())
                .parsedName(notification.getParsedName())
                .parsedAmount(notification.getParsedAmount())
                .result(notification.getResult())
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId())
                .applicationId(notification.getApplicationId())
                .exceptionCaseId(notification.getExceptionCaseId())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
