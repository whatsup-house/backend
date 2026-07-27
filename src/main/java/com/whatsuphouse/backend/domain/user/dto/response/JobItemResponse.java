package com.whatsuphouse.backend.domain.user.dto.response;

import com.whatsuphouse.backend.domain.user.enums.Job;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobItemResponse {

    @Schema(description = "세부직업 코드", example = "NURSE")
    private String code;

    @Schema(description = "세부직업 라벨", example = "간호사")
    private String label;

    public static JobItemResponse from(Job job) {
        return JobItemResponse.builder()
                .code(job.getCode())
                .label(job.getLabel())
                .build();
    }
}
