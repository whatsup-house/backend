package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.request.AdminApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminApplicationService {

    private final ApplicationRepository applicationRepository;

    public List<AdminApplicationResponse> getAllApplications() {
        return applicationRepository.findByDeletedAtIsNull()
                .stream()
                .map(AdminApplicationResponse::from)
                .toList();
    }

    public List<AdminApplicationResponse> getApplicationsByGathering(UUID gatheringId) {
        return applicationRepository.findByGatheringIdAndDeletedAtIsNull(gatheringId)
                .stream()
                .map(AdminApplicationResponse::from)
                .toList();
    }

    public List<AdminApplicationResponse> getApplicationsByStatus(ApplicationStatus status) {
        return applicationRepository.findByStatusAndDeletedAtIsNull(status)
                .stream()
                .map(AdminApplicationResponse::from)
                .toList();
    }

    public AdminApplicationResponse getApplication(UUID id) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        return AdminApplicationResponse.from(application);
    }

    @Transactional
    public AdminApplicationResponse changeStatus(UUID id, AdminApplicationStatusRequest request) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        ApplicationStatus newStatus = request.getStatus();
        switch (newStatus) {
            case CONFIRMED -> application.confirm();
            case CANCELLED -> application.cancel();
            case ATTENDED -> application.attend();
            default -> throw new CustomException(ErrorCode.CANNOT_CANCEL);
        }

        return AdminApplicationResponse.from(application);
    }
}
