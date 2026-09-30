package com.whatsuphouse.backend.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiningTableMoveRequest {

    @NotNull
    @Schema(description = "옮길 테이블 ID(같은 회차)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID targetTableId;

    @Size(max = 500, message = "사유는 500자 이하로 입력해주세요.")
    @Schema(description = "사유. 확정(CONFIRMED) 테이블이 끼면 필수", example = "지인끼리 분리 요청", nullable = true)
    private String reason;
}
