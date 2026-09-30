package com.whatsuphouse.backend.domain.dining.client.service;

import com.whatsuphouse.backend.domain.dining.client.dto.request.FeedbackCreateRequest;
import com.whatsuphouse.backend.domain.dining.client.dto.request.SafetyReportCreateRequest;
import com.whatsuphouse.backend.domain.dining.client.dto.response.DiningHistoryResponse;
import com.whatsuphouse.backend.domain.dining.entity.ExceptionCase;
import com.whatsuphouse.backend.domain.dining.entity.Feedback;
import com.whatsuphouse.backend.domain.dining.entity.PeerPreference;
import com.whatsuphouse.backend.domain.dining.entity.SafetyReport;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.repository.FeedbackRepository;
import com.whatsuphouse.backend.domain.dining.repository.PeerPreferenceRepository;
import com.whatsuphouse.backend.domain.dining.repository.SafetyReportRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 우연한 식탁 참가자의 테이블 피드백·신고·참가 이력. (설계 2.6, KAN-350)
 * 사람별 선호(PeerPreference)·신고(SafetyReport)는 운영자·매칭 전용이라 참가자 응답에 담지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiningService {

    // 실제로 열린(확정·종료) 테이블. 피드백·참가 이력·피드백 요약의 대상.
    public static final Set<DiningTableStatus> HELD_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);

    private final MatchingService matchingService;
    private final ExceptionCaseService exceptionCaseService;
    private final FeedbackRepository feedbackRepository;
    private final PeerPreferenceRepository peerPreferenceRepository;
    private final SafetyReportRepository safetyReportRepository;
    private final VenueRepository venueRepository;

    /**
     * 피드백과 사람별 선호를 한 트랜잭션으로 저장한다.
     * 테이블 멤버가 아니면 403, 피드백 가능 상태가 아니면 400, 이미 남겼으면 409, peers가 같은 테이블 다른 멤버가 아니거나 겹치면 400.
     */
    @Transactional
    public void submitFeedback(UUID tableId, UUID userId, FeedbackCreateRequest request) {
        DiningTable table = matchingService.findTable(tableId);
        List<DiningTableMember> members = matchingService.listActiveTableMembers(tableId);
        DiningTableMember me = findMember(members, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TABLE_MEMBER));
        if (!isFeedbackOpen(table, LocalDateTime.now())) {
            throw new CustomException(ErrorCode.FEEDBACK_NOT_OPEN);
        }
        if (feedbackRepository.existsByTableMemberId(me.getId())) {
            throw new CustomException(ErrorCode.FEEDBACK_ALREADY_SUBMITTED);
        }

        Set<UUID> others = members.stream().map(DiningService::userIdOf).filter(Objects::nonNull)
                .filter(id -> !id.equals(userId)).collect(Collectors.toSet());
        List<FeedbackCreateRequest.Peer> peers = request.getPeers() != null ? request.getPeers() : List.of();
        Set<UUID> seen = new HashSet<>();
        for (FeedbackCreateRequest.Peer peer : peers) {
            if (!others.contains(peer.getUserId()) || !seen.add(peer.getUserId())) {
                throw new CustomException(ErrorCode.INVALID_TABLE_PEER);
            }
        }

        feedbackRepository.save(Feedback.builder()
                .tableMemberId(me.getId())
                .tableScore(request.getTableScore())
                .talkScore(request.getTalkScore())
                .venueScore(request.getVenueScore())
                .rejoinIntent(request.getRejoinIntent())
                .comment(StringUtils.hasText(request.getComment()) ? request.getComment().trim() : null)
                .build());
        peerPreferenceRepository.saveAll(peers.stream()
                .map(peer -> new PeerPreference(userId, peer.getUserId(), peer.getKind(), tableId))
                .toList());
    }

    /**
     * 같은 테이블 멤버 신고. 예외함에 SAFETY 건(신청 = 피신고자 신청)을 만들고 신고에 연결한다.
     * 신고자가 멤버가 아니면 403, 자기 자신이면 400, 대상이 같은 테이블 멤버가 아니면 400.
     */
    @Transactional
    public void reportMember(UUID tableId, UUID reporterId, SafetyReportCreateRequest request) {
        DiningTable table = matchingService.findTable(tableId);
        List<DiningTableMember> members = matchingService.listActiveTableMembers(tableId);
        DiningTableMember reporter = findMember(members, reporterId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_TABLE_MEMBER));
        if (reporterId.equals(request.getReportedUserId())) {
            throw new CustomException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        DiningTableMember reported = findMember(members, request.getReportedUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TABLE_PEER));

        String reason = request.getReason().trim();
        ExceptionCase exceptionCase = exceptionCaseService.open(ExceptionCaseType.SAFETY, table.getSession().getId(),
                tableId, reported.getApplication().getId(),
                "참가자 신고 (신고자: %s, 대상: %s) %s".formatted(displayName(reporter), displayName(reported), reason));
        safetyReportRepository.save(
                new SafetyReport(reporterId, request.getReportedUserId(), tableId, reason, exceptionCase.getId()));
    }

    /** 확정·종료 테이블 멤버십 기준 참가 이력, 최신 회차 순. 취소한 신청은 빠진다. */
    public List<DiningHistoryResponse> listMyHistory(UUID userId) {
        List<DiningTableMember> memberships = matchingService.listUserTableMembers(userId, HELD_TABLE_STATUSES);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<UUID> submitted = Set.copyOf(feedbackRepository.findTableMemberIdsIn(
                memberships.stream().map(DiningTableMember::getId).toList()));
        Map<UUID, String> venueNames = venueRepository.findAllById(memberships.stream()
                        .map(member -> member.getTable().getVenueId()).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));
        return memberships.stream()
                .map(member -> {
                    DiningTable table = member.getTable();
                    GatheringSession session = table.getSession();
                    return DiningHistoryResponse.builder()
                            .tableId(table.getId())
                            .sessionId(session.getId())
                            .eventDate(session.getEventDate())
                            // 회차에 지역 컬럼이 없어 장소명을 지역으로 쓴다(DiningApplicationListResponse와 같은 규칙).
                            .region(session.getLocation() != null ? session.getLocation().getName() : null)
                            // v1 테이블은 식당 풀 대신 이름을 직접 적어 두었다.
                            .venueName(table.getVenueId() != null
                                    ? venueNames.get(table.getVenueId()) : table.getRestaurantName())
                            .tableStatus(table.getStatus())
                            .feedbackSubmitted(submitted.contains(member.getId()))
                            .build();
                })
                .toList();
    }

    // 확정·종료 테이블이고, 회차 날짜가 지났거나 당일 종료 시각이 지났을 때. 종료 시각이 없으면 다음 날부터.
    static boolean isFeedbackOpen(DiningTable table, LocalDateTime now) {
        if (!HELD_TABLE_STATUSES.contains(table.getStatus())) {
            return false;
        }
        GatheringSession session = table.getSession();
        return session.getEventDate().isBefore(now.toLocalDate())
                || (session.getEndTime() != null && !now.isBefore(session.getEventDate().atTime(session.getEndTime())));
    }

    private static Optional<DiningTableMember> findMember(List<DiningTableMember> members, UUID userId) {
        return members.stream().filter(member -> userId.equals(userIdOf(member))).findFirst();
    }

    private static UUID userIdOf(DiningTableMember member) {
        User user = member.getApplication().getUser();
        return user != null ? user.getId() : null;
    }

    private static String displayName(DiningTableMember member) {
        User user = member.getApplication().getUser();
        return user != null && user.getNickname() != null ? user.getNickname() : member.getApplication().getName();
    }
}
