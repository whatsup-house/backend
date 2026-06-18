package com.whatsuphouse.backend.domain.user.dto.response;

import com.whatsuphouse.backend.domain.user.enums.JobCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JobGroupResponse {

    @Schema(description = "직업군 코드", example = "MEDICAL")
    private String category;

    @Schema(description = "직업군 라벨", example = "의료·보건")
    private String categoryLabel;

    private List<JobItemResponse> jobs;

    public static JobGroupResponse of(JobCategory category, List<JobItemResponse> jobs) {
        return JobGroupResponse.builder()
                .category(category.name())
                .categoryLabel(category.getLabel())
                .jobs(jobs)
                .build();
    }
}
