package com.whatsuphouse.backend.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiningTableSplitRequest {

    @NotEmpty
    @Schema(description = "새 테이블로 떼어 낼 테이블 멤버 ID", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    private List<@NotNull UUID> memberIds;

    @Size(max = 500, message = "사유는 500자 이하로 입력해주세요.")
    @Schema(description = "사유. 확정(CONFIRMED) 테이블이면 필수", example = "인원이 많아 두 테이블로 분리", nullable = true)
    private String reason;
}
