package com.whatsuphouse.backend.domain.application.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPaymentRequest {

    @NotNull(message = "입금 확인 여부는 필수입니다.")
    @Schema(example = "true", description = "입금 확인 여부 (true=입금 완료 처리, false=입금 확인 해제)")
    private Boolean confirmed;
}
