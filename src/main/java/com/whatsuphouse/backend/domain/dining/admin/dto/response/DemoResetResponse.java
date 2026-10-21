package com.whatsuphouse.backend.domain.dining.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DemoResetResponse {

    @Schema(description = "지운 신청 수(취소된 신청 포함)", example = "12")
    private int deletedApplicationCount;

    @Schema(description = "지운 테이블 수(해체된 테이블 포함)", example = "3")
    private int deletedTableCount;

    @Schema(description = "새로 넣은 심사용 신청자 수", example = "16")
    private int seededApplicantCount;
}
