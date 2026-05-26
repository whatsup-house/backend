package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationDeleteResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.user.entity.User;
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
    private final MileageService mileageService;

    public List<AdminApplicationResponse> getAllApplications(UUID gatheringId, ApplicationStatus status) {
        return applicationRepository.findApplications(gatheringId, status)
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
    public ApplicationDeleteResponse deleteApplication(UUID id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() == ApplicationStatus.ATTENDED) {
            throw new CustomException(ErrorCode.CANNOT_DELETE);
        }

        application.cancel();
        return ApplicationDeleteResponse.from(application);
    }

    @Transactional
    public ApplicationStatusResponse changeStatus(UUID id, ApplicationStatusRequest request) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        ApplicationStatus newStatus = request.getStatus();
       switch (newStatus) {
    case CONFIRMED -> application.confirm();
    case ATTENDED -> {
        if (application.getStatus() == ApplicationStatus.ATTENDED) {
            throw new CustomException(ErrorCode.ALREADY_ATTENDED);
        }
        application.attend();
        return rewardAttendanceMileage(application);
    }
    default -> throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
}


        return ApplicationStatusResponse.of(application.getId(), application.getStatus(), null, null);
    }

    private ApplicationStatusResponse rewardAttendanceMileage(Application application) {
        User user = application.getUser();
        if (user == null) {
            return ApplicationStatusResponse.of(application.getId(), application.getStatus(), null, null);
        }

        MileageHistory history = mileageService.rewardAttendance(user, application.getId());
        return ApplicationStatusResponse.of(
                application.getId(),
                application.getStatus(),
                history.getAmount(),
                history.getBalanceAfter()
        );
    }
}
