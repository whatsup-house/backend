package com.whatsuphouse.backend.domain.user.service;

import com.whatsuphouse.backend.domain.user.dto.response.JobGroupResponse;
import com.whatsuphouse.backend.domain.user.dto.response.JobItemResponse;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.enums.JobCategory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 직업군 + 세부직업 카탈로그 제공. 프론트 검색형 콤보박스가 사용한다. (KAN-271)
 */
@Service
public class JobService {

    public List<JobGroupResponse> getJobCatalog() {
        return Arrays.stream(JobCategory.values())
                .map(category -> JobGroupResponse.of(category, jobsOf(category)))
                .toList();
    }

    private List<JobItemResponse> jobsOf(JobCategory category) {
        return Arrays.stream(Job.values())
                .filter(job -> job.getCategory() == category)
                .map(JobItemResponse::from)
                .toList();
    }
}
