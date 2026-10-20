package com.whatsuphouse.backend.domain.application.service;

import com.whatsuphouse.backend.domain.application.client.service.MembershipWeekRule;
import com.whatsuphouse.backend.domain.application.repository.ApplicationCandidateSessionRepository;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MembershipWeekRuleTest {

    @Mock
    private ApplicationCandidateSessionRepository applicationCandidateSessionRepository;

    @InjectMocks
    private MembershipWeekRule membershipWeekRule;

    private final UUID userId = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 19);

    @Test
    @DisplayName("희망 회차가 서로 다른 주면 CANDIDATE_SESSIONS_DIFFERENT_WEEKS, 활성 신청은 조회하지 않는다")
    void check_differentWeeks() {
        assertThat(membershipWeekRule.check(userId, null, List.of(monday.plusDays(3), monday.plusDays(7))))
                .contains(ErrorCode.CANDIDATE_SESSIONS_DIFFERENT_WEEKS);
        then(applicationCandidateSessionRepository).should(never())
                .findActiveApplicationIdsInDateRange(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("그 주(월~일)에 다른 활성 신청이 있으면 MEMBERSHIP_WEEKLY_LIMIT")
    void check_otherActiveInWeek() {
        given(applicationCandidateSessionRepository.findActiveApplicationIdsInDateRange(
                eq(userId), eq(GatheringType.RANDOM_TABLE), any(), eq(monday), eq(monday.plusDays(6))))
                .willReturn(List.of(UUID.randomUUID()));

        assertThat(membershipWeekRule.check(userId, null, List.of(monday.plusDays(1), monday.plusDays(3))))
                .contains(ErrorCode.MEMBERSHIP_WEEKLY_LIMIT);
    }

    @Test
    @DisplayName("그 주의 활성 신청이 검사 대상 신청 자신뿐이면 위반이 아니다(입금 확정 시점 검사)")
    void check_onlySelfActive_passes() {
        UUID self = UUID.randomUUID();
        given(applicationCandidateSessionRepository.findActiveApplicationIdsInDateRange(
                eq(userId), eq(GatheringType.RANDOM_TABLE), any(), eq(monday), eq(monday.plusDays(6))))
                .willReturn(List.of(self));

        assertThat(membershipWeekRule.check(userId, self, List.of(monday.plusDays(3)))).isEmpty();
    }
}
