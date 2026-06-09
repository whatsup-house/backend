package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationDeleteResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationAttendedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final MileageService mileageService;
    private final ApplicationEventPublisher eventPublisher;

    public List<AdminApplicationResponse> getAllApplications(UUID gatheringId, ApplicationStatus status) {
        List<Application> applications = applicationRepository.findApplications(gatheringId, status);
        Map<UUID, List<AnswerView>> answersByApplicationId = loadAnswersByApplicationId(applications);

        return applications.stream()
                .map(application -> AdminApplicationResponse.from(
                        application,
                        answersByApplicationId.getOrDefault(application.getId(), List.of())))
                .toList();
    }

    public AdminApplicationResponse getApplication(UUID id) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
        List<AnswerView> answers = applicationAnswerRepository.findDetailByApplicationId(id)
                .stream()
                .map(AnswerView::from)
                .toList();
        return AdminApplicationResponse.from(application, answers);
    }

    private Map<UUID, List<AnswerView>> loadAnswersByApplicationId(List<Application> applications) {
        List<UUID> applicationIds = applications.stream()
                .map(Application::getId)
                .toList();
        if (applicationIds.isEmpty()) {
            return Map.of();
        }

        return applicationAnswerRepository.findByApplicationIds(applicationIds)
                .stream()
                .collect(Collectors.groupingBy(
                        answer -> answer.getApplication().getId(),
                        Collectors.mapping(AnswerView::from, Collectors.toList())));
    }

    @Transactional
    public ApplicationDeleteResponse deleteApplication(UUID id) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() == ApplicationStatus.ATTENDED) {
            throw new CustomException(ErrorCode.CANNOT_DELETE);
        }

        application.cancel();
        // 관리자 직접 삭제도 취소 알림 대상 (FR-NTF-04)
        eventPublisher.publishEvent(new ApplicationCancelledEvent(application));
        return ApplicationDeleteResponse.from(application);
    }

    @Transactional
    public ApplicationStatusResponse changeStatus(UUID id, ApplicationStatusRequest request) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        ApplicationStatus newStatus = request.getStatus();
        switch (newStatus) {
            case CONFIRMED -> {
                application.confirm();
                // 확정 알림 이메일 (FR-NTF-03)
                eventPublisher.publishEvent(new ApplicationConfirmedEvent(application));
            }
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
        // 참석 확인 + 마일리지 적립 안내 이메일 (FR-NTF-05)
        // 적립 금액과 잔액을 이벤트에 담아 이메일 본문에서 바로 사용할 수 있도록 합니다.
        eventPublisher.publishEvent(new ApplicationAttendedEvent(
                application, history.getAmount(), history.getBalanceAfter()));
        return ApplicationStatusResponse.of(
                application.getId(),
                application.getStatus(),
                history.getAmount(),
                history.getBalanceAfter()
        );
    }
}
