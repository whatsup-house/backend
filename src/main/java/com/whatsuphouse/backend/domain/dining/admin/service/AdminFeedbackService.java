package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.dining.admin.dto.response.FeedbackSummaryResponse;
import com.whatsuphouse.backend.domain.dining.client.service.DiningService;
import com.whatsuphouse.backend.domain.dining.entity.Feedback;
import com.whatsuphouse.backend.domain.dining.enums.RejoinIntent;
import com.whatsuphouse.backend.domain.dining.repository.FeedbackRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** 회차 피드백 요약(운영자). 사람별 선호·신고 내용은 담지 않고 신고 수만 센다. (KAN-350) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminFeedbackService {

    private final GatheringService gatheringService;
    private final MatchingService matchingService;
    private final FeedbackRepository feedbackRepository;
    private final SafetyReportRepository safetyReportRepository;

    /** 확정·종료 테이블(취소한 신청 제외) 기준. 평균은 소수 둘째 자리, 응답이 없으면 null. */
    public FeedbackSummaryResponse getFeedbackSummary(UUID sessionId) {
        gatheringService.findRandomTableSession(sessionId);
        List<DiningTableMember> members = matchingService.listSessionTableMembers(sessionId, DiningService.HELD_TABLE_STATUSES);
        if (members.isEmpty()) {
            return summary(List.of(), 0, 0, List.of());
        }
        Map<UUID, UUID> tableIdByMemberId = members.stream()
                .collect(Collectors.toMap(DiningTableMember::getId, member -> member.getTable().getId()));
        List<Feedback> feedbacks = feedbackRepository.findByTableMemberIdIn(tableIdByMemberId.keySet());
        Map<UUID, List<Feedback>> feedbacksByTable = feedbacks.stream()
                .collect(Collectors.groupingBy(feedback -> tableIdByMemberId.get(feedback.getTableMemberId())));
        List<UUID> tableIds = members.stream().map(member -> member.getTable().getId()).distinct().toList();

        List<FeedbackSummaryResponse.TableSummary> byTable = tableIds.stream()
                .map(tableId -> {
                    List<Feedback> tableFeedbacks = feedbacksByTable.getOrDefault(tableId, List.of());
                    return FeedbackSummaryResponse.TableSummary.builder()
                            .tableId(tableId)
                            .responseCount(tableFeedbacks.size())
                            .avgTable(average(tableFeedbacks, Feedback::getTableScore))
                            .avgTalk(average(tableFeedbacks, Feedback::getTalkScore))
                            .avgVenue(average(tableFeedbacks, Feedback::getVenueScore))
                            .build();
                })
                .toList();
        return summary(feedbacks, members.size(), safetyReportRepository.countByTableIdIn(tableIds), byTable);
    }

    private static FeedbackSummaryResponse summary(List<Feedback> feedbacks, int memberCount, long reportCount,
                                                   List<FeedbackSummaryResponse.TableSummary> byTable) {
        Map<RejoinIntent, Long> rejoin = feedbacks.stream()
                .collect(Collectors.groupingBy(Feedback::getRejoinIntent, Collectors.counting()));
        return FeedbackSummaryResponse.builder()
                .responseCount(feedbacks.size())
                .memberCount(memberCount)
                .responseRate(memberCount == 0 ? 0 : round((double) feedbacks.size() / memberCount))
                .avgTable(average(feedbacks, Feedback::getTableScore))
                .avgTalk(average(feedbacks, Feedback::getTalkScore))
                .avgVenue(average(feedbacks, Feedback::getVenueScore))
                .rejoinYes(rejoin.getOrDefault(RejoinIntent.YES, 0L))
                .rejoinMaybe(rejoin.getOrDefault(RejoinIntent.MAYBE, 0L))
                .rejoinNo(rejoin.getOrDefault(RejoinIntent.NO, 0L))
                .reportCount(reportCount)
                .byTable(byTable)
                .build();
    }

    private static Double average(List<Feedback> feedbacks, ToIntFunction<Feedback> score) {
        return feedbacks.isEmpty() ? null : round(feedbacks.stream().mapToInt(score).average().orElseThrow());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
