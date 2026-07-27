package com.whatsuphouse.backend.domain.user.controller;

import com.whatsuphouse.backend.domain.user.dto.response.JobGroupResponse;
import com.whatsuphouse.backend.domain.user.service.JobService;
import com.whatsuphouse.backend.global.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "직업", description = "직업군·세부직업 카탈로그 API")
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "직업 카탈로그 조회", description = "직업군별 세부직업 목록을 반환한다. 회원가입 직업 선택 콤보박스용.")
    @GetMapping
    public ApiResult<List<JobGroupResponse>> getJobs() {
        return ApiResult.success(jobService.getJobCatalog());
    }
}
