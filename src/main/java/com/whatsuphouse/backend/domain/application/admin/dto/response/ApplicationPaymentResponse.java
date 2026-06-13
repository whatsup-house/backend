package com.whatsuphouse.backend.domain.application.admin.dto.response;

import com.whatsuphouse.backend.domain.application.entity.Application;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ApplicationPaymentResponse {

    private UUID id;
    private boolean paid;               // 유료 게더링 여부 (입금 개념 적용 대상인지)
    private boolean paymentConfirmed;   // 입금 확인 여부
    private LocalDateTime paymentConfirmedAt;

    public static ApplicationPaymentResponse from(Application application) {
        return ApplicationPaymentResponse.builder()
                .id(application.getId())
                .paid(application.isPaidGathering())
                .paymentConfirmed(application.isPaymentConfirmed())
                .paymentConfirmedAt(application.getPaymentConfirmedAt())
                .build();
    }
}
