package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchRunResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingResultResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingRunResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.MatchRunRepository;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 관리자 v1 매칭 API(/api/admin/gatherings/{id}/matching, /api/admin/matching). 수동 조정 재설계는 KAN-347. */
@Service
@RequiredArgsConstructor
public class MatchingService {

    public static final int DEFAULT_GROUP_SIZE = 4;

    private static final Set<DiningTableStatus> ACTIVE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED);
    private static final Set<DiningTableStatus> VISIBLE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);

    private final GatheringService gatheringService;
    private final ApplicationRepository applicationRepository;
    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final MatchRunRepository matchRunRepository;
    private final DiningMatchService diningMatchService;
    private final DiningAttendanceService diningAttendanceService;

    /**
     * 기존 API 호환(KAN-338 전까지): 경로의 gatheringId는 회차 ID다.
     * rule-v2 엔진으로 실행하되 v1처럼 그룹 인원을 groupSize로 고정한다(최소 = 최대). (KAN-345)
     */
    @Transactional
    public MatchingRunResponse runMatching(UUID gatheringId, int groupSize) {
        GatheringSession session = gatheringService.findSession(gatheringId);
        if (session.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.MATCHING_NOT_ALLOWED);
        }
        // 그룹 인원 수는 2~8 범위로 보정, 벗어나면 기본값 사용. (KAN-224)
        int size = (groupSize >= 2 && groupSize <= 8) ? groupSize : DEFAULT_GROUP_SIZE;
        MatchRunResponse run = diningMatchService.runMatch(session.getId(), MatchRunTrigger.MANUAL, null, size);
        return MatchingRunResponse.builder()
                .gatheringId(gatheringId)
                .algorithmVersion(run.getAlgorithmVersion())
                .confirmedCount(run.getCandidateCount())
                .groupCount(run.getTableCount())
                .matchedCount(run.getCandidateCount() - run.getUnassignedCount())
                .unmatchedCount(run.getUnassignedCount())
                .build();
    }

    // ── 관리자 검토 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MatchingResultResponse getMatchingResult(UUID gatheringId) {
        UUID sessionId = gatheringService.findSession(gatheringId).getId();

        // 해체(DISSOLVED)된 테이블은 이력이라 보여주지 않는다.
        List<DiningTable> tables = diningTableRepository
                .findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(sessionId, VISIBLE_TABLE_STATUSES);
        List<UUID> tableIds = tables.stream().map(DiningTable::getId).toList();
        List<DiningTableMember> members = tableIds.isEmpty()
                ? List.of()
                : diningTableMemberRepository.findByTableIdsWithApplication(tableIds);

        Map<UUID, List<DiningTableMember>> byTable = members.stream()
                .collect(Collectors.groupingBy(m -> m.getTable().getId()));
        Set<UUID> matchedAppIds = members.stream()
                .map(m -> m.getApplication().getId())
                .collect(Collectors.toSet());

        List<MatchingResultResponse.GroupView> groupViews = tables.stream()
                .map(t -> MatchingResultResponse.GroupView.builder()
                        .groupId(t.getId())
                        .eventDate(t.getEventDate())
                        .status(t.getStatus())
                        .groupScore(t.getGroupScore())
                        .groupSize(t.getGroupSize())
                        .restaurantName(t.getRestaurantName())
                        .restaurantAddress(t.getRestaurantAddress())
                        .venueId(t.getVenueId())
                        .members(byTable.getOrDefault(t.getId(), List.of()).stream()
                                .map(this::toMemberView).toList())
                        .build())
                .toList();

        List<MatchingResultResponse.MemberView> unmatched = applicationRepository
                .findBySession_IdAndStatusAndDeletedAtIsNull(sessionId, ApplicationStatus.CONFIRMED).stream()
                .filter(a -> !matchedAppIds.contains(a.getId()))
                .map(a -> MatchingResultResponse.MemberView.builder()
                        .applicationId(a.getId()).name(a.getName()).phone(a.getPhone()).build())
                .toList();

        return MatchingResultResponse.builder()
                .gatheringId(gatheringId).groups(groupViews).unmatched(unmatched).build();
    }

    private MatchingResultResponse.MemberView toMemberView(DiningTableMember m) {
        return MatchingResultResponse.MemberView.builder()
                .memberId(m.getId())
                .applicationId(m.getApplication().getId())
                .name(m.getApplication().getName())
                .phone(m.getApplication().getPhone())
                .seatOrder(m.getSeatOrder())
                .manualAssign(m.isManual())
                .build();
    }

    @Transactional
    public void moveMember(UUID memberId, UUID targetGroupId) {
        DiningTableMember member = diningTableMemberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));
        DiningTable oldTable = member.getTable();
        DiningTable target = findTable(targetGroupId);

        member.moveTo(target, diningTableMemberRepository.countByTable_Id(targetGroupId) + 1);
        // 같은 멤버 행이 옮겨 가므로 참석 행도 따라간다. 확정 테이블이면 확정 규칙 적용, 확정 전 테이블이면 참석 행 정리. (KAN-349)
        diningAttendanceService.seat(member);
        diningTableMemberRepository.flush();
        oldTable.updateGroupSize(diningTableMemberRepository.countByTable_Id(oldTable.getId()));
        target.updateGroupSize(diningTableMemberRepository.countByTable_Id(targetGroupId));
        diningMatchService.rescoreTable(oldTable);
        diningMatchService.rescoreTable(target);
    }

    @Transactional
    public void excludeMember(UUID memberId) {
        DiningTableMember member = diningTableMemberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));
        DiningTable table = member.getTable();
        // TODO(KAN-347 머지 후): 행 삭제 대신 removed_at 표시로 바꿔 멤버·참석 이력을 남긴다(DiningTableService 방식으로 통일).
        diningAttendanceService.releaseSeat(memberId);
        diningTableMemberRepository.delete(member);
        diningTableMemberRepository.flush();
        table.updateGroupSize(diningTableMemberRepository.countByTable_Id(table.getId()));
        diningMatchService.rescoreTable(table);
    }

    @Transactional
    public void assignMember(UUID groupId, UUID applicationId) {
        DiningTable table = findTable(groupId);
        // 해체된 테이블의 멤버 행은 이력이므로 활성 테이블 배정만 막는다.
        if (diningTableMemberRepository.existsByApplication_IdAndTable_StatusIn(applicationId, ACTIVE_TABLE_STATUSES)) {
            throw new CustomException(ErrorCode.MATCHING_ALREADY_ASSIGNED);
        }
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        DiningTableMember member = diningTableMemberRepository.save(DiningTableMember.builder()
                .application(application)
                .table(table)
                .seatOrder(diningTableMemberRepository.countByTable_Id(groupId) + 1)
                .assignReason(AssignReason.MANUAL)
                .isManual(true)
                .build());
        // 확정 테이블에 새로 앉으면 확정 파이프라인의 개인 규칙(배정 회차·매칭 CONFIRMED·참석 SCHEDULED)을 적용한다. (KAN-349)
        diningAttendanceService.seat(member);
        diningTableMemberRepository.flush();
        table.updateGroupSize(diningTableMemberRepository.countByTable_Id(groupId));
        diningMatchService.rescoreTable(table);
    }

    @Transactional
    public void confirmGroup(UUID groupId) {
        findTable(groupId).confirm();
    }

    @Transactional
    public void updateRestaurant(UUID groupId, String name, String address) {
        findTable(groupId).updateRestaurant(name, address);
    }

    public DiningTable findTable(UUID tableId) {
        return diningTableRepository.findById(tableId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
    }

    /** 회차별·상태별 테이블 수. 테이블이 없는 회차는 결과에 없다. (KAN-348 운영 대시보드) */
    @Transactional(readOnly = true)
    public Map<UUID, Map<DiningTableStatus, Long>> countTablesBySessionIds(Collection<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return diningTableRepository.countBySessionIdsGroupByStatus(sessionIds).stream()
                .collect(Collectors.groupingBy(DiningTableRepository.SessionStatusCountProjection::getSessionId,
                        Collectors.toMap(DiningTableRepository.SessionStatusCountProjection::getStatus,
                                DiningTableRepository.SessionStatusCountProjection::getCount)));
    }

    /** 매칭 실행 기록이 있는 회차 ID. (KAN-348 운영 대시보드) */
    @Transactional(readOnly = true)
    public Set<UUID> findSessionIdsWithMatchRun(Collection<UUID> sessionIds) {
        return sessionIds.isEmpty() ? Set.of() : Set.copyOf(matchRunRepository.findSessionIdsIn(sessionIds));
    }

    /** 조원 중 회원의 userId (채팅 단체방 멤버 프리필용). 비회원 신청은 제외. */
    @Transactional(readOnly = true)
    public List<UUID> listGroupMemberUserIds(UUID groupId) {
        findTable(groupId);
        return diningTableMemberRepository.findByTableIdWithApplication(groupId).stream()
                .map(member -> member.getApplication().getUser())
                .filter(Objects::nonNull)
                .map(User::getId)
                .distinct()
                .toList();
    }

    /**
     * 신청별 활성(PROPOSED|CONFIRMED) 테이블. 활성 테이블에 앉지 않은 신청은 결과에 없다. 우연한 식탁 내 신청 조회의 테이블 요약용. (KAN-342)
     */
    @Transactional(readOnly = true)
    public Map<UUID, DiningTable> findTablesByApplicationIds(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        // 활성 테이블은 신청당 1개를 서비스가 보장하지만 DB 제약은 없어서, 겹치면 먼저 온 행을 쓴다.
        return diningTableMemberRepository.findByApplicationIdsWithTable(applicationIds, ACTIVE_TABLE_STATUSES).stream()
                .collect(Collectors.toMap(member -> member.getApplication().getId(), DiningTableMember::getTable,
                        (first, second) -> first));
    }
}
