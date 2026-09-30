package com.whatsuphouse.backend.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class DiningTableMergeRequest {

    @NotNull
    @Size(min = 2, message = "합칠 테이블을 2개 이상 골라주세요.")
    @Schema(description = "합칠 테이블 ID(같은 회차). 첫 테이블로 합치고 나머지는 해체",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\", \"7c9e6679-7425-40de-944b-e07fc1f90ae7\"]")
    private List<@NotNull UUID> tableIds;

    @Size(max = 500, message = "사유는 500자 이하로 입력해주세요.")
    @Schema(description = "사유. 확정(CONFIRMED) 테이블이 끼면 필수", example = "취소로 인원이 줄어 병합", nullable = true)
    private String reason;
}
