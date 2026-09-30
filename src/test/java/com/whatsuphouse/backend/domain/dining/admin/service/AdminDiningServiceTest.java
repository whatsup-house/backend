package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningDashboardResponse;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.enums.MatchingGroupStatus;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDiningServiceTest {

    @Mock
    private GatheringService gatheringService;

    @Mock
    private AdminApplicationService adminApplicationService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private ExceptionCaseRepository exceptionCaseRepository;

    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private AdminDiningService adminDiningService;

    private final Gathering gathering = Gathering.builder().title("우연한 식탁").gatheringType(GatheringType.RANDOM_TABLE).build();

    private GatheringSession session(int daysLater) {
        GatheringSession session = GatheringSession.builder()
                .gathering(gathering).eventDate(LocalDate.now().plusDays(daysLater)).maxAttendees(12).build();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private Application application() {
        Application application = Application.builder()
                .bookingNumber("WH261001-" + UUID.randomUUID().toString().substring(0, 6))
                .gathering(gathering).name("홍길동").phone("01012345678").build();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        return application;
    }

    @Test
    @DisplayName("대시보드 타일은 여러 회차를 희망한 신청을 한 번만 세고, 반려 신청은 대기에서 뺀다")
    void getDashboard_countsDistinctApplicants() {
        GatheringSession first = session(3);
        GatheringSession second = session(10);
        Application shared = application(); // 두 회차 모두 희망, 결제 완료
        shared.confirm();
        Application unpaid = application(); // 결제 대기
        unpaid.awaitPayment();
        Application rejected = application(); // 반려
        rejected.reject("정원 초과");

        given(gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE)).willReturn(List.of(first, second));
        given(adminApplicationService.listSessionApplications(first.getId())).willReturn(List.of(shared, unpaid));
        given(adminApplicationService.listSessionApplications(second.getId())).willReturn(List.of(shared, rejected));
        given(matchingService.countGroupsBySessionIds(List.of(first.getId(), second.getId())))
                .willReturn(Map.of(first.getId(), Map.of(MatchingGroupStatus.PENDING, 2L, MatchingGroupStatus.CONFIRMED, 1L)));
        given(exceptionCaseRepository.countBySessionIdsAndStatus(any(), eq(ExceptionCaseStatus.OPEN)))
                .willReturn(List.of(new ExceptionCaseRepository.SessionCountProjection() {
                    public UUID getSessionId() { return second.getId(); }
                    public Long getCount() { return 3L; }
                }));
        given(exceptionCaseRepository.countByStatus(ExceptionCaseStatus.OPEN)).willReturn(5L);

        DiningDashboardResponse dashboard = adminDiningService.getDashboard();

        assertThat(dashboard.getUpcomingSessionCount()).isEqualTo(2);
        assertThat(dashboard.getWaitingApplicantCount()).isEqualTo(2);   // shared + unpaid
        assertThat(dashboard.getPaidApplicantCount()).isEqualTo(1);      // shared
        assertThat(dashboard.getProposedTableCount()).isEqualTo(2);
        assertThat(dashboard.getConfirmedTableCount()).isEqualTo(1);
        assertThat(dashboard.getOpenExceptionCount()).isEqualTo(5);

        DiningDashboardResponse.SessionCard firstCard = dashboard.getSessions().get(0);
        assertThat(firstCard.getPaidCount()).isEqualTo(1);
        assertThat(firstCard.getMaxAttendees()).isEqualTo(12);
        assertThat(firstCard.getTableCount()).isEqualTo(3);
        assertThat(firstCard.getIsMatchRunDone()).isTrue();
        assertThat(firstCard.getOpenExceptionCount()).isZero();

        DiningDashboardResponse.SessionCard secondCard = dashboard.getSessions().get(1);
        assertThat(secondCard.getPaidCount()).isEqualTo(1);
        assertThat(secondCard.getTableCount()).isZero();
        assertThat(secondCard.getIsMatchRunDone()).isFalse();
        assertThat(secondCard.getOpenExceptionCount()).isEqualTo(3);
    }
}
