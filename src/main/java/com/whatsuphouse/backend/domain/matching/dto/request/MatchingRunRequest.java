package com.whatsuphouse.backend.domain.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingRunRequest {

    // 그룹당 인원 수. 미지정(null) 시 기본 4명으로 동작한다. (KAN-224)
    @Schema(description = "그룹당 인원 수 (2~8, 미지정 시 4)", example = "4")
    @Min(2)
    @Max(8)
    private Integer groupSize;
}
