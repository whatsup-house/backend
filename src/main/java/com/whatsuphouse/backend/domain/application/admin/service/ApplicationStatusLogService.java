package com.whatsuphouse.backend.domain.application.admin.service;

import com.whatsuphouse.backend.domain.application.admin.dto.response.ApplicationStatusHistoryResponse;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationStatusLog;
import com.whatsuphouse.backend.domain.application.enums.DiningPaymentStatus;
import com.whatsuphouse.backend.domain.application.enums.StatusLogField;
import com.whatsuphouse.backend.domain.application.event.ApplicationStatusChangedEvent;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.application.repository.ApplicationStatusLogRepository;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.service.MatchResolutionService;
import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.ticket.service.TicketService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.service.UserService;
import com.whatsuphouse.backend.global.auth.UserPrincipal;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 신청 상태 변경 이력(PRD 16장, KAN-437). 상태를 바꾼 서비스가 ApplicationStatusChangedEvent를 발행하면 같은 트랜잭션에서
 * 신청 상태·매칭 상태·통합 결제 상태를 항목별 마지막 기록과 비교해 바뀐 항목만 남긴다(발행이 롤백되면 이력도 롤백).
 * 마지막 기록과 비교하므로 발행을 빠뜨린 변경도 다음 발행 때 따라잡힌다(사유·시각은 그 발행 기준).
 * 행위자는 요청한 회원(관리자·참가자), 인증 없는 스케줄러·웹훅이면 null(시스템).
 * ponytail: 발행 1건마다 조회 4번(이력·이용권·보증금·해결 선택). 매칭 실행 수십 명은 감당한다. 수백 명이면 신청 ID 묶음 이벤트로.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicationStatusLogService {

    private static final int REASON_MAX_LENGTH = 200;

    private final ApplicationStatusLogRepository applicationStatusLogRepository;
    private final ApplicationRepository applicationRepository;
    private final TicketService ticketService;
    private final DepositService depositService;
    private final MatchResolutionService matchResolutionService;
    private final UserService userService;

    @EventListener
    @Transactional
    public void onStatusChanged(ApplicationStatusChangedEvent event) {
        Application application = event.getApplication();
        Map<StatusLogField, String> current = new EnumMap<>(StatusLogField.class);
        current.put(StatusLogField.STATUS, name(application.getStatus()));
        current.put(StatusLogField.MATCH_STATUS, name(application.getMatchStatus()));
        current.put(StatusLogField.PAYMENT, name(paymentStatusOf(application)));

        // 최신순이라 항목별 첫 값이 마지막 기록이다.
        Map<StatusLogField, String> last = new EnumMap<>(StatusLogField.class);
        applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId())
                .forEach(log -> last.putIfAbsent(log.getField(), log.getToValue()));

        UUID actorId = currentActorId();
        String reason = truncate(event.getReason());
        current.forEach((field, value) -> {
            boolean recorded = last.containsKey(field);
            if ((recorded || value != null) && !Objects.equals(last.get(field), value)) {
                applicationStatusLogRepository.save(
                        ApplicationStatusLog.of(application.getId(), field, last.get(field), value, actorId, reason));
            }
        });
    }

    /** 신청 상태 변경 이력, 최신순. 취소된 신청도 조회한다. 신청이 없으면 404. */
    public List<ApplicationStatusHistoryResponse> listHistory(UUID applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new CustomException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        List<ApplicationStatusLog> logs = applicationStatusLogRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        Map<UUID, User> actors = userService.findUsersByIds(logs.stream()
                .map(ApplicationStatusLog::getActorId).filter(Objects::nonNull).distinct().toList());
        return logs.stream()
                .map(log -> {
                    User actor = log.getActorId() != null ? actors.get(log.getActorId()) : null;
                    return ApplicationStatusHistoryResponse.of(log, actor != null ? actor.getName() : null);
                })
                .toList();
    }

    private DiningPaymentStatus paymentStatusOf(Application application) {
        if (application.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            return DiningPaymentStatus.of(application, null, null, false);
        }
        List<UUID> ids = List.of(application.getId());
        Deposit deposit = depositService.findByApplicationIds(ids).get(application.getId());
        return DiningPaymentStatus.of(application,
                ticketService.findDeductionStatuses(ids).get(application.getId()),
                deposit != null ? deposit.getStatus() : null,
                matchResolutionService.findOfferedResolutionIds(ids).containsKey(application.getId()));
    }

    private static UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal.getUserId() : null;
    }

    private static String name(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > REASON_MAX_LENGTH ? trimmed.substring(0, REASON_MAX_LENGTH) : trimmed;
    }
}
