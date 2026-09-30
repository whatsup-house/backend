package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.dining.admin.dto.response.FeedbackSummaryResponse;
import com.whatsuphouse.backend.domain.dining.client.service.DiningService;
import com.whatsuphouse.backend.domain.dining.entity.Feedback;
import com.whatsuphouse.backend.domain.dining.enums.RejoinIntent;
import com.whatsuphouse.backend.domain.dining.repository.FeedbackRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackServiceTest {

    @Mock
    private GatheringService gatheringService;
    @Mock
    private MatchingService matchingService;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private SafetyReportRepository safetyReportRepository;

    @InjectMocks
    private AdminFeedbackService adminFeedbackService;

    private final UUID sessionId = UUID.randomUUID();
    private final List<DiningTableMember> members = new ArrayList<>();

    private DiningTable table() {
        DiningTable table = DiningTable.builder().groupSize(0).algorithmVersion("rule-v2").build();
        ReflectionTestUtils.setField(table, "id", UUID.randomUUID());
        return table;
    }

    private UUID member(DiningTable table) {
        DiningTableMember member = DiningTableMember.builder().table(table).assignReason(AssignReason.INITIAL).build();
        ReflectionTestUtils.setField(member, "id", UUID.randomUUID());
        members.add(member);
        return member.getId();
    }

    private static Feedback feedback(UUID memberId, int table, int talk, int venue, RejoinIntent rejoin) {
        return Feedback.builder().tableMemberId(memberId).tableScore(table).talkScore(talk).venueScore(venue)
                .rejoinIntent(rejoin).build();
    }

    @Test
    @DisplayName("응답률·항목 평균·재참여 의향·신고 수를 회차 전체와 테이블별로 집계한다")
    void getFeedbackSummary_aggregates() {
        DiningTable a = table();
        DiningTable b = table();
        UUID a1 = member(a);
        UUID a2 = member(a);
        member(a);
        UUID b1 = member(b);
        member(b);
        given(matchingService.listSessionTableMembers(sessionId, DiningService.HELD_TABLE_STATUSES)).willReturn(members);
        given(feedbackRepository.findByTableMemberIdIn(anyCollection())).willReturn(List.of(
                feedback(a1, 5, 4, 3, RejoinIntent.YES),
                feedback(a2, 4, 4, 5, RejoinIntent.MAYBE),
                feedback(b1, 3, 2, 1, RejoinIntent.NO)));
        given(safetyReportRepository.countByTableIdIn(List.of(a.getId(), b.getId()))).willReturn(1L);

        FeedbackSummaryResponse summary = adminFeedbackService.getFeedbackSummary(sessionId);

        assertThat(summary)
                .extracting(FeedbackSummaryResponse::getResponseCount, FeedbackSummaryResponse::getMemberCount,
                        FeedbackSummaryResponse::getResponseRate, FeedbackSummaryResponse::getAvgTable,
                        FeedbackSummaryResponse::getAvgTalk, FeedbackSummaryResponse::getAvgVenue,
                        FeedbackSummaryResponse::getRejoinYes, FeedbackSummaryResponse::getRejoinMaybe,
                        FeedbackSummaryResponse::getRejoinNo, FeedbackSummaryResponse::getReportCount)
                .containsExactly(3, 5, 0.6, 4.0, 3.33, 3.0, 1L, 1L, 1L, 1L);
        assertThat(summary.getByTable())
                .extracting(FeedbackSummaryResponse.TableSummary::getTableId,
                        FeedbackSummaryResponse.TableSummary::getResponseCount,
                        FeedbackSummaryResponse.TableSummary::getAvgTable,
                        FeedbackSummaryResponse.TableSummary::getAvgTalk,
                        FeedbackSummaryResponse.TableSummary::getAvgVenue)
                .containsExactly(tuple(a.getId(), 2, 4.5, 4.0, 4.0), tuple(b.getId(), 1, 3.0, 2.0, 1.0));
    }

    @Test
    @DisplayName("확정·종료 테이블이 없으면 0건·평균 null")
    void getFeedbackSummary_noTables() {
        given(matchingService.listSessionTableMembers(sessionId, DiningService.HELD_TABLE_STATUSES)).willReturn(List.of());

        FeedbackSummaryResponse summary = adminFeedbackService.getFeedbackSummary(sessionId);

        assertThat(summary.getResponseCount()).isZero();
        assertThat(summary.getResponseRate()).isZero();
        assertThat(summary.getAvgTable()).isNull();
        assertThat(summary.getByTable()).isEmpty();
    }
}
