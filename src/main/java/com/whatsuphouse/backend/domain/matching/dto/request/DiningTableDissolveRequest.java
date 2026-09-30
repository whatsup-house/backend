package com.whatsuphouse.backend.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiningTableDissolveRequest {

    @Size(max = 500, message = "사유는 500자 이하로 입력해주세요.")
    @Schema(description = "사유. 확정(CONFIRMED) 테이블이면 필수", example = "식당 사정으로 해체 후 재배치", nullable = true)
    private String reason;
}
