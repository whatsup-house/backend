package com.whatsuphouse.backend.domain.application.client.service;

import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 멤버십 주 1회 규칙: 희망 회차가 한 ISO 주(월~일) 안에 있어야 하고, 그 주에 다른 활성 신청이 없어야 한다. (KAN-401)
 * 신청(ApplicationService)과 이용권 입금 확정(TicketService.settlePass)이 같이 쓴다. TicketService에 의존하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipWeekRule {

    // '활성' 신청으로 보는 상태.
    private static final List<ApplicationStatus> ACTIVE_STATUSES = List.of(
            ApplicationStatus.PENDING, ApplicationStatus.PAYMENT_PENDING, ApplicationStatus.CONFIRMED, ApplicationStatus.ATTENDED);

    private final ApplicationCandidateSessionRepository applicationCandidateSessionRepository;

    /**
     * 위반 사유. 없으면 빈 값.
     * @param applicationId 이미 저장된 검사 대상 신청(자기 자신은 '다른 활성 신청'에서 뺀다). 저장 전이면 null.
     */
    public Optional<ErrorCode> check(UUID userId, UUID applicationId, Collection<LocalDate> candidateDates) {
        Set<LocalDate> mondays = candidateDates.stream()
                .map(date -> date.with(DayOfWeek.MONDAY))
                .collect(Collectors.toSet());
        if (mondays.size() > 1) {
            return Optional.of(ErrorCode.CANDIDATE_SESSIONS_DIFFERENT_WEEKS);
        }
        if (mondays.isEmpty()) {
            return Optional.empty();
        }
        LocalDate monday = mondays.iterator().next();
        boolean otherActive = applicationCandidateSessionRepository.findActiveApplicationIdsInDateRange(
                        userId, GatheringType.RANDOM_TABLE, ACTIVE_STATUSES, monday, monday.plusDays(6)).stream()
                .anyMatch(id -> !id.equals(applicationId));
        return otherActive ? Optional.of(ErrorCode.MEMBERSHIP_WEEKLY_LIMIT) : Optional.empty();
    }

    /** 신청의 희망 회차 날짜(우선순위 순). */
    public List<LocalDate> candidateDates(UUID applicationId) {
        return applicationCandidateSessionRepository.findByApplicationIdsWithSession(List.of(applicationId)).stream()
                .map(candidate -> candidate.getSession().getEventDate())
                .toList();
    }
}
