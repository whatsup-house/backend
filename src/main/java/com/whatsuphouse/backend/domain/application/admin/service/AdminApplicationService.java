package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationPaymentRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.request.ApplicationStatusRequest;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationDeleteResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.AdminApplicationResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationPaymentResponse;
import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusResponse;
import com.whatsuphouse.backend.domain.application.client.dto.response.AnswerView;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.form.repository.ApplicationAnswerRepository;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.service.MileageService;
import com.whatsuphouse.backend.domain.notification.event.ApplicationAttendedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationCancelledEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationPaymentConfirmedEvent;
import com.whatsuphouse.backend.domain.notification.event.ApplicationApprovedEvent;
import com.whatsuphouse.backend.domain.participant.entity.Participant;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
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
    private final TicketService ticketService;
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

        // 우연한 식탁 회원 신청을 관리자가 취소하면 차감했던 이용권을 환불한다. (KAN-261)
        // 게더링이 이미 취소된 경우엔 게더링 취소 시점에 일괄 환불되므로 중복 환불하지 않는다.
        if (application.getUser() != null
                && application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE
                && application.getGathering().getStatus() != GatheringStatus.CANCELLED) {
            ticketService.refundOneTicket(application);
        }

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
                if (application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE) {
                    approveRandomTableApplication(application);
                } else {
                    enforceCapacityForNewSeat(application);
                    application.confirm();
                    eventPublisher.publishEvent(new ApplicationConfirmedEvent(application));
                }
            }
            case REJECTED -> {
                String reason = request.getRejectionReason();
                if (reason == null || reason.isBlank()) {
                    throw new CustomException(ErrorCode.REJECTION_REASON_REQUIRED);
                }
                application.reject(reason.trim());
                if (application.getGathering().getGatheringType() == GatheringType.RANDOM_TABLE) {
                    application.getParticipant().rejectRandomTable();
                }
            }
            case ATTENDED -> {
                if (application.getStatus() == ApplicationStatus.ATTENDED) {
                    throw new CustomException(ErrorCode.ALREADY_ATTENDED);
                }
                // PENDING에서 CONFIRMED를 건너뛰고 바로 출석 처리하면 새로 좌석을 차지하므로 정원을 검사한다.
                // 이미 CONFIRMED(좌석 보유)였다면 검사에서 통과된다.
                enforceCapacityForNewSeat(application);
                application.attend();
                return rewardAttendanceMileage(application);
            }
            default -> throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        return ApplicationStatusResponse.of(application.getId(), application.getStatus(), null, null);
    }

    private void approveRandomTableApplication(Application application) {
        Participant participant = application.getParticipant();
        if (participant.isAccountBlocked()) {
            throw new CustomException(ErrorCode.PARTICIPANT_BLOCKED);
        }
        if (participant.isRandomTableEligibilityRestricted()) {
            throw new CustomException(ErrorCode.RANDOM_TABLE_ELIGIBILITY_RESTRICTED);
        }

        participant.approveRandomTable();
        if (!application.requiresRandomTableTicket()) {
            enforceCapacityForNewSeat(application);
            application.confirm();
            eventPublisher.publishEvent(new ApplicationConfirmedEvent(application));
        } else if (ticketService.tryUseOneTicket(participant, application)) {
            enforceCapacityForNewSeat(application);
            application.confirm();
            eventPublisher.publishEvent(new ApplicationConfirmedEvent(application));
        } else {
            application.awaitPayment();
            eventPublisher.publishEvent(new ApplicationApprovedEvent(application));
        }
    }

    // 입금 확인/해제 토글. 신청 상태(status)와 독립적으로 동작하며 확정을 자동 트리거하지 않는다. (KAN-242)
    @Transactional
    public ApplicationPaymentResponse changePayment(UUID id, ApplicationPaymentRequest request) {
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        boolean alreadyConfirmed = application.isPaymentConfirmed();
        if (Boolean.TRUE.equals(request.getConfirmed())) {
            application.confirmPayment();
            // 입금 확인 중 → 완료로 처음 넘어가는 순간에만 입금 완료 안내 메일 발송 (재확인/해제는 제외)
            if (!alreadyConfirmed) {
                eventPublisher.publishEvent(new ApplicationPaymentConfirmedEvent(application));
            }
        } else {
            application.cancelPayment();
        }
        return ApplicationPaymentResponse.from(application);
    }

    /**
     * 새로 좌석을 차지하는 상태 변경(PENDING→CONFIRMED, PENDING→ATTENDED) 시점에 정원을 검사한다.
     * 정원은 CONFIRMED+ATTENDED만 차지하므로, 이미 좌석을 가진 신청의 재확정/출석 처리는 통과시킨다. (KAN-236)
     */
    private void enforceCapacityForNewSeat(Application application) {
        if (ApplicationStatus.SEAT_OCCUPYING.contains(application.getStatus())) {
            return;
        }
        int occupiedSeats = applicationRepository.countByGatheringIdAndStatusInAndDeletedAtIsNull(
                application.getGathering().getId(), ApplicationStatus.SEAT_OCCUPYING);
        if (occupiedSeats >= application.getGathering().getMaxAttendees()) {
            throw new CustomException(ErrorCode.GATHERING_FULL);
        }
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
