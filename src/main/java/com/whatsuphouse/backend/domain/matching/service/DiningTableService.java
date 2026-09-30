package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.chat.service.AdminChatService;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseType;
import com.whatsuphouse.backend.domain.dining.service.ExceptionCaseService;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableAdjustResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.AssignReason;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.service.DiningMatchService.TableEvaluator;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 테이블 멤버 구성 변경: 확정 후 취소 재조정(설계 4.7)과 운영자 수동 조정(이동·분리·병합·해체, 설계 5.3).
 * 수동 조정은 관련 테이블을 전부 하드 조건으로 재검증하고, 위반이 있으면 400 TABLE_RULE_VIOLATION으로 롤백한다. (KAN-347)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DiningTableService {

    private static final Set<DiningTableStatus> ACTIVE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED);

    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final DiningMatchService diningMatchService;
    private final AdminApplicationService adminApplicationService;
    private final SessionVenueService sessionVenueService;
    private final ExceptionCaseService exceptionCaseService;
    private final AdminChatService adminChatService;
    // 좌석이 바뀔 때 참석(Attendance) 행을 맞춘다(확정 테이블에 앉으면 SCHEDULED, 떠나면 삭제). (KAN-349)
    private final DiningAttendanceService diningAttendanceService;

    // ── 확정 후 취소 재조정 ──────────────────────────────────────────────────────

    /**
     * 취소된 멤버를 빼고, 남은 인원이 최소 인원 미만이면
     * (1) 같은 회차 REALLOCATING 대기자 중 하드 조건을 지키는 사람을 점수 순으로 충원,
     * (2) 안 되면 남은 멤버를 같은 회차 다른 활성 테이블(최대 인원 미만)로 전원 옮기고 이 테이블은 해체,
     * (3) 그것도 안 되면 예외함(CONFLICT)에 올려 운영자가 병합·해체를 정한다.
     */
    public void rebalance(UUID tableId) {
        DiningTable table = lockTable(tableId);
        if (!table.isActive()) {
            return;
        }
        List<DiningTableMember> members = diningTableMemberRepository.findByTableIdWithApplication(tableId);
        List<DiningTableMember> cancelled = members.stream()
                .filter(member -> member.getApplication().getStatus() == ApplicationStatus.CANCELLED)
                .toList();
        List<DiningTableMember> remaining = members.stream().filter(member -> !cancelled.contains(member)).toList();
        if (!cancelled.isEmpty()) {
            table.recordReallocation("CANCEL_REMOVE", null, "참가자 취소", memberIds(cancelled));
            // 행은 지우지 않고 removed_at만 채운다(참석 기록이 이 행을 참조한다). 이후 조회·인원·검증에서 빠진다.
            // 취소 멤버의 참석은 호출 전에 DiningAttendanceService.cancelAttendance가 CANCELED_EARLY로 남긴다(KAN-349).
            cancelled.forEach(DiningTableMember::remove);
            diningTableMemberRepository.flush();
            syncChat(table, List.of(), cancelled);
        }

        List<Application> waiting = listReallocatingApplications(table.getSession().getId());
        TableEvaluator evaluator = diningMatchService.evaluator(table.getSession(),
                Stream.concat(applications(remaining).stream(), waiting.stream()).toList(), tableId);
        List<UUID> seated = applicationIds(remaining);
        if (seated.size() >= evaluator.rules().tableSizeMin()) {
            settle(table, evaluator, seated);
            return;
        }

        List<Application> refill = pickRefill(evaluator, seated, waiting);
        if (!refill.isEmpty()) {
            List<DiningTableMember> added = new ArrayList<>();
            int seat = remaining.size() + 1;
            for (Application application : refill) {
                added.add(diningTableMemberRepository.save(DiningTableMember.builder()
                        .application(application)
                        .table(table)
                        .seatOrder(seat++)
                        .assignReason(AssignReason.REALLOCATED)
                        .isManual(false)
                        .build()));
                application.changeMatchStatus(seatedStatus(table));
            }
            added.forEach(diningAttendanceService::seat);
            settle(table, evaluator, Stream.concat(seated.stream(), refill.stream().map(Application::getId)).toList());
            table.recordReallocation("REFILL", null, "취소 후 대기자 충원", memberIds(added));
            syncChat(table, added, List.of());
            return;
        }

        if (moveOut(table, remaining)) {
            return;
        }
        settle(table, evaluator, seated);
        exceptionCaseService.open(ExceptionCaseType.CONFLICT, table.getSession().getId(), tableId, null,
                "취소 후 인원이 최소 인원 미만(" + seated.size() + "/" + evaluator.rules().tableSizeMin()
                        + "명)이고 충원·재배치할 수 없습니다. 병합·해체를 결정해 주세요.");
    }

    // 남은 인원이 최소 인원에 닿을 때까지 대기자를 한 명씩 더한다. 매번 하드 조건(인원 제외)을 지키는 사람 중 그룹 점수가 가장 높은 사람.
    // 최소 인원까지 채울 수 없으면 빈 목록.
    private static List<Application> pickRefill(TableEvaluator evaluator, List<UUID> seated, List<Application> waiting) {
        List<UUID> trialSeats = new ArrayList<>(seated);
        List<Application> pool = new ArrayList<>(waiting);
        List<Application> picked = new ArrayList<>();
        while (trialSeats.size() < evaluator.rules().tableSizeMin()) {
            Application best = null;
            double bestScore = -1;
            for (Application candidate : pool) {
                List<UUID> trial = append(trialSeats, candidate.getId());
                if (!fits(evaluator, trial)) {
                    continue;
                }
                double score = evaluator.score(trial).value().doubleValue();
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
            if (best == null) {
                return List.of();
            }
            trialSeats.add(best.getId());
            pool.remove(best);
            picked.add(best);
        }
        return picked;
    }

    // 남은 멤버를 한 명씩 들어갈 수 있는 다른 활성 테이블 중 점수가 가장 높은 곳에 배치한다. 전원 배치될 때만 실제로 옮긴다.
    private boolean moveOut(DiningTable table, List<DiningTableMember> remaining) {
        List<DiningTable> targets = diningTableRepository
                .findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(table.getSession().getId(), ACTIVE_TABLE_STATUSES)
                .stream()
                .filter(target -> !target.getId().equals(table.getId()))
                .toList();
        if (targets.isEmpty() && !remaining.isEmpty()) {
            return false;
        }
        Map<UUID, List<DiningTableMember>> targetMembers = targets.isEmpty() ? Map.of()
                : diningTableMemberRepository.findByTableIdsWithApplication(targets.stream().map(DiningTable::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(member -> member.getTable().getId()));
        // ponytail: 대상 테이블마다 평가기를 따로 읽는다(테이블 수만큼 조회). 회차당 테이블이 수십 개를 넘으면 한 번에 읽도록 묶는다.
        Map<UUID, TableEvaluator> evaluators = new LinkedHashMap<>();
        Map<UUID, List<UUID>> seating = new LinkedHashMap<>();
        for (DiningTable target : targets) {
            List<DiningTableMember> current = targetMembers.getOrDefault(target.getId(), List.of());
            evaluators.put(target.getId(), diningMatchService.evaluator(target.getSession(),
                    Stream.concat(applications(current).stream(), applications(remaining).stream()).toList(), target.getId()));
            seating.put(target.getId(), new ArrayList<>(applicationIds(current)));
        }

        Map<DiningTableMember, DiningTable> plan = new LinkedHashMap<>();
        for (DiningTableMember member : remaining) {
            DiningTable best = null;
            double bestScore = -1;
            for (DiningTable target : targets) {
                List<UUID> trial = append(seating.get(target.getId()), member.getApplication().getId());
                TableEvaluator evaluator = evaluators.get(target.getId());
                if (!fits(evaluator, trial)) {
                    continue;
                }
                double score = evaluator.score(trial).value().doubleValue();
                if (score > bestScore) {
                    best = target;
                    bestScore = score;
                }
            }
            if (best == null) {
                return false;
            }
            seating.get(best.getId()).add(member.getApplication().getId());
            plan.put(member, best);
        }

        Map<DiningTable, List<DiningTableMember>> movedByTarget = new LinkedHashMap<>();
        plan.forEach((member, target) -> {
            List<DiningTableMember> moved = movedByTarget.computeIfAbsent(target, t -> new ArrayList<>());
            moved.add(member);
            int seat = targetMembers.getOrDefault(target.getId(), List.of()).size() + moved.size();
            member.moveTo(target, seat, AssignReason.REALLOCATED, false);
            member.getApplication().changeMatchStatus(seatedStatus(target));
            diningAttendanceService.seat(member);
        });
        movedByTarget.forEach((target, moved) -> {
            settle(target, evaluators.get(target.getId()), seating.get(target.getId()));
            target.recordReallocation("REBALANCE_IN", null, "취소 후 인원 부족 테이블에서 이동", memberIds(moved));
            syncChat(target, moved, List.of());
        });
        table.recordReallocation("REBALANCE_OUT", null, "취소 후 인원 부족으로 다른 테이블에 재배치", memberIds(remaining));
        syncChat(table, List.of(), remaining);
        dissolve(table);
        return true;
    }

    // 같은 회차 REALLOCATING 대기자: 결제 완료, 참여 자격 승인, 활성 테이블 없음. 입력(신청) 순서가 동점 처리 기준.
    private List<Application> listReallocatingApplications(UUID sessionId) {
        List<Application> reallocating = adminApplicationService.listSessionApplications(sessionId).stream()
                .filter(a -> a.getStatus() == ApplicationStatus.CONFIRMED && a.getMatchStatus() == MatchStatus.REALLOCATING)
                .filter(a -> a.getUser() != null && a.getUser().isApprovedForRandomTable())
                .toList();
        if (reallocating.isEmpty()) {
            return List.of();
        }
        Set<UUID> seated = Set.copyOf(diningTableMemberRepository.findApplicationIdsByTableStatusIn(
                applicationIdsOf(reallocating), ACTIVE_TABLE_STATUSES));
        return reallocating.stream()
                .filter(a -> !seated.contains(a.getId()))
                .sorted(Comparator.comparing(Application::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Application::getId))
                .toList();
    }

    // ── 운영자 수동 조정 ─────────────────────────────────────────────────────────

    /** 멤버를 같은 회차 다른 테이블로 옮긴다. */
    public DiningTableAdjustResponse moveMember(UUID tableId, UUID memberId, UUID targetTableId, String reason, UUID adminId) {
        DiningTable source = lockAdjustableTable(tableId);
        DiningTable target = lockAdjustableTable(targetTableId);
        List<DiningTable> tables = List.of(source, target);
        checkSameSession(tables);
        checkReason(tables, reason);
        DiningTableMember member = diningTableMemberRepository.findByIdAndRemovedAtIsNull(memberId)
                .filter(m -> m.getTable().getId().equals(tableId))
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));

        member.moveTo(target, diningTableMemberRepository.countByTable_IdAndRemovedAtIsNull(targetTableId) + 1);
        member.getApplication().changeMatchStatus(seatedStatus(target));
        diningAttendanceService.seat(member);
        validateAndRecord("MOVE", adminId, reason, tables, List.of(member));
        syncChat(source, List.of(), List.of(member));
        syncChat(target, List.of(member), List.of());
        return response(source.getSession().getId(), tables);
    }

    /** v1 호환(PATCH /api/admin/matching/members/{memberId}/move). 사유가 없으므로 확정 테이블은 조정할 수 없다. */
    public void moveMember(UUID memberId, UUID targetTableId, UUID adminId) {
        DiningTableMember member = diningTableMemberRepository.findByIdAndRemovedAtIsNull(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND));
        moveMember(member.getTable().getId(), memberId, targetTableId, null, adminId);
    }

    /** 고른 멤버를 새 PROPOSED 테이블로 떼어 낸다. 새 테이블의 자동 확정 시각은 원본과 같다. */
    public DiningTableAdjustResponse splitTable(UUID tableId, List<UUID> memberIds, String reason, UUID adminId) {
        DiningTable source = lockAdjustableTable(tableId);
        checkReason(List.of(source), reason);
        Set<UUID> ids = new LinkedHashSet<>(memberIds);
        List<DiningTableMember> moving = diningTableMemberRepository.findByTableIdWithApplication(tableId).stream()
                .filter(member -> ids.contains(member.getId()))
                .toList();
        if (moving.size() != ids.size()) {
            throw new CustomException(ErrorCode.MATCHING_MEMBER_NOT_FOUND);
        }

        DiningTable created = diningTableRepository.save(DiningTable.builder()
                .session(source.getSession())
                .matchRunId(source.getMatchRunId())
                .eventDate(source.getEventDate())
                .region(source.getRegion())
                .groupSize(moving.size())
                .algorithmVersion(source.getAlgorithmVersion())
                .confirmAt(source.getConfirmAt())
                .build());
        int seat = 1;
        for (DiningTableMember member : moving) {
            member.moveTo(created, seat++);
            member.getApplication().changeMatchStatus(MatchStatus.CONFIRM_PENDING);
            // 새 테이블은 제안(PROPOSED)이라 확정 테이블에서 떠난 좌석의 참석 행을 지운다.
            diningAttendanceService.releaseSeat(member.getId());
        }
        List<DiningTable> tables = List.of(source, created);
        validateAndRecord("SPLIT", adminId, reason, tables, moving);
        syncChat(source, List.of(), moving);
        return response(source.getSession().getId(), tables);
    }

    /** 첫 테이블로 나머지 테이블의 멤버를 합치고 나머지는 해체한다. */
    public DiningTableAdjustResponse mergeTables(List<UUID> tableIds, String reason, UUID adminId) {
        List<DiningTable> tables = new LinkedHashSet<>(tableIds).stream().map(this::lockAdjustableTable).toList();
        if (tables.size() < 2) {
            throw new CustomException(ErrorCode.TABLE_SESSION_MISMATCH);
        }
        checkSameSession(tables);
        checkReason(tables, reason);
        DiningTable target = tables.get(0);
        List<DiningTable> others = tables.subList(1, tables.size());
        List<DiningTableMember> moving = diningTableMemberRepository
                .findByTableIdsWithApplication(others.stream().map(DiningTable::getId).toList());
        Map<DiningTable, List<DiningTableMember>> movingBySource = moving.stream()
                .collect(Collectors.groupingBy(DiningTableMember::getTable, LinkedHashMap::new, Collectors.toList()));

        int seat = diningTableMemberRepository.countByTable_IdAndRemovedAtIsNull(target.getId()) + 1;
        for (DiningTableMember member : moving) {
            member.moveTo(target, seat++);
            member.getApplication().changeMatchStatus(seatedStatus(target));
            diningAttendanceService.seat(member);
        }
        others.forEach(this::dissolve);
        validateAndRecord("MERGE", adminId, reason, tables, moving);
        movingBySource.forEach((source, members) -> syncChat(source, List.of(), members));
        syncChat(target, moving, List.of());
        return response(target.getSession().getId(), tables);
    }

    /** 테이블을 해체하고 멤버를 재배치 대기(REALLOCATING)로 돌린다. 멤버 행은 이력으로 남는다. */
    public DiningTableAdjustResponse dissolveTable(UUID tableId, String reason, UUID adminId) {
        DiningTable table = lockAdjustableTable(tableId);
        checkReason(List.of(table), reason);
        List<DiningTableMember> members = diningTableMemberRepository.findByTableIdWithApplication(tableId);
        members.forEach(member -> {
            member.getApplication().changeMatchStatus(MatchStatus.REALLOCATING);
            // 멤버 행은 해체된 테이블에 이력으로 남고 좌석은 없어지므로 참석 행을 지운다.
            diningAttendanceService.releaseSeat(member.getId());
        });
        dissolve(table);
        validateAndRecord("DISSOLVE", adminId, reason, List.of(table), members);
        syncChat(table, List.of(), members);
        return response(table.getSession().getId(), List.of(table));
    }

    /**
     * v1 호환(POST /api/admin/matching/groups/{groupId}/members): 미배정 신청을 테이블에 넣는다.
     * 이 회차 신청(배정 또는 희망)이고 결제 완료(CONFIRMED)이며 활성 테이블에 없는 신청만. 확정 테이블은 사유가 필요하다.
     */
    public DiningTableAdjustResponse assignMember(UUID tableId, UUID applicationId, String reason, UUID adminId) {
        DiningTable table = lockAdjustableTable(tableId);
        checkReason(List.of(table), reason);
        Application application = adminApplicationService.listSessionApplications(table.getSession().getId()).stream()
                .filter(a -> a.getId().equals(applicationId) && a.getStatus() == ApplicationStatus.CONFIRMED)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_ASSIGNABLE));
        if (diningTableMemberRepository.existsByApplication_IdAndRemovedAtIsNullAndTable_StatusIn(applicationId, ACTIVE_TABLE_STATUSES)) {
            throw new CustomException(ErrorCode.MATCHING_ALREADY_ASSIGNED);
        }

        DiningTableMember member = diningTableMemberRepository.save(DiningTableMember.builder()
                .application(application)
                .table(table)
                .seatOrder(diningTableMemberRepository.countByTable_IdAndRemovedAtIsNull(tableId) + 1)
                .assignReason(AssignReason.MANUAL)
                .isManual(true)
                .build());
        application.changeMatchStatus(seatedStatus(table));
        diningAttendanceService.seat(member);
        validateAndRecord("ASSIGN", adminId, reason, List.of(table), List.of(member));
        syncChat(table, List.of(member), List.of());
        return response(table.getSession().getId(), List.of(table));
    }

    // 조작 결과의 활성 테이블을 전부 하드 조건으로 재검증한다. 위반이 있으면 예외로 트랜잭션째 되돌리고,
    // 통과하면 인원·점수를 다시 매기고 잠근 뒤(재실행 보존) 관련 테이블 모두에 이력을 남긴다.
    private void validateAndRecord(String action, UUID adminId, String reason, List<DiningTable> tables,
                                   List<DiningTableMember> changedMembers) {
        diningTableMemberRepository.flush();
        List<DiningTable> active = tables.stream().filter(DiningTable::isActive).toList();
        Map<UUID, List<DiningTableMember>> membersByTable = active.isEmpty() ? Map.of()
                : diningTableMemberRepository.findByTableIdsWithApplication(active.stream().map(DiningTable::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(member -> member.getTable().getId()));

        List<DiningTableAdjustResponse.Violation> violations = new ArrayList<>();
        Map<DiningTable, TableEvaluator> evaluators = new LinkedHashMap<>();
        for (DiningTable table : active) {
            List<DiningTableMember> members = membersByTable.getOrDefault(table.getId(), List.of());
            TableEvaluator evaluator = diningMatchService.evaluator(table.getSession(), applications(members), table.getId());
            evaluators.put(table, evaluator);
            evaluator.violations(applicationIds(members)).forEach(rule -> violations.add(
                    new DiningTableAdjustResponse.Violation(table.getId(), rule, message(rule, members.size(), evaluator.rules()))));
        }
        if (!violations.isEmpty()) {
            throw new CustomException(ErrorCode.TABLE_RULE_VIOLATION, Map.of("violations", violations));
        }

        String trimmedReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        List<UUID> changedIds = memberIds(changedMembers);
        evaluators.forEach((table, evaluator) ->
                settle(table, evaluator, applicationIds(membersByTable.getOrDefault(table.getId(), List.of()))));
        for (DiningTable table : tables) {
            table.lock();
            table.recordReallocation(action, adminId, trimmedReason, changedIds);
        }
    }

    private DiningTableAdjustResponse response(UUID sessionId, List<DiningTable> tables) {
        Set<UUID> ids = tables.stream().map(DiningTable::getId).collect(Collectors.toSet());
        return DiningTableAdjustResponse.builder()
                .tables(diningMatchService.getTables(sessionId).getTables().stream()
                        .filter(view -> ids.contains(view.getId()))
                        .toList())
                .validation(new DiningTableAdjustResponse.Validation(true, List.of()))
                .build();
    }

    private static String message(MatchingEngine.HardRule rule, int size, MatchingEngine.Rules rules) {
        return switch (rule) {
            case TABLE_SIZE -> "인원 " + size + "명이 " + rules.tableSizeMin() + "~" + rules.tableSizeMax() + "명 범위를 벗어납니다.";
            case AGE_GAP -> "출생연도 차이가 " + rules.maxAgeGap() + "년을 넘습니다.";
            case BLOCKED_PAIR -> "같은 테이블에 앉을 수 없는 관계(피하고 싶음·신고)인 멤버가 있습니다.";
        };
    }

    // ── 공통 ────────────────────────────────────────────────────────────────

    private DiningTable lockTable(UUID tableId) {
        return diningTableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCHING_GROUP_NOT_FOUND));
    }

    private DiningTable lockAdjustableTable(UUID tableId) {
        DiningTable table = lockTable(tableId);
        if (!table.isActive()) {
            throw new CustomException(ErrorCode.TABLE_NOT_ADJUSTABLE);
        }
        return table;
    }

    private static void checkSameSession(List<DiningTable> tables) {
        Set<UUID> tableIds = new HashSet<>();
        UUID sessionId = tables.get(0).getSession().getId();
        for (DiningTable table : tables) {
            if (!tableIds.add(table.getId()) || !table.getSession().getId().equals(sessionId)) {
                throw new CustomException(ErrorCode.TABLE_SESSION_MISMATCH);
            }
        }
    }

    // 확정 테이블 조정은 "긴급 수정"이라 사유를 남겨야 한다(MAT-14).
    private static void checkReason(List<DiningTable> tables, String reason) {
        boolean confirmed = tables.stream().anyMatch(table -> table.getStatus() == DiningTableStatus.CONFIRMED);
        if (confirmed && (reason == null || reason.isBlank())) {
            throw new CustomException(ErrorCode.TABLE_ADJUST_REASON_REQUIRED);
        }
    }

    // 하드 조건 중 인원 하한은 채우는 중이라 빼고, 상한·나이 차·제외 관계만 본다.
    private static boolean fits(TableEvaluator evaluator, List<UUID> applicationIds) {
        return applicationIds.size() <= evaluator.rules().tableSizeMax()
                && evaluator.violations(applicationIds).stream().allMatch(rule -> rule == MatchingEngine.HardRule.TABLE_SIZE);
    }

    private static void settle(DiningTable table, TableEvaluator evaluator, List<UUID> applicationIds) {
        MatchingEngine.Score score = evaluator.score(applicationIds);
        table.updateGroupSize(applicationIds.size());
        table.updateScore(score.value(), score.detail());
    }

    private void dissolve(DiningTable table) {
        if (table.getVenueId() != null) {
            sessionVenueService.releaseTable(table.getSession().getId(), table.getVenueId());
        }
        table.dissolve();
    }

    // 확정 테이블 멤버는 확정, 제안 테이블 멤버는 확정 대기.
    private static MatchStatus seatedStatus(DiningTable table) {
        return table.getStatus() == DiningTableStatus.CONFIRMED ? MatchStatus.CONFIRMED : MatchStatus.CONFIRM_PENDING;
    }

    // 채팅방은 확정 파이프라인(KAN-346)이 만든다. 아직 없으면 건너뛴다.
    private void syncChat(DiningTable table, Collection<DiningTableMember> joined, Collection<DiningTableMember> left) {
        if (table.getChatRoomId() == null) {
            return;
        }
        adminChatService.syncTableMembers(table.getChatRoomId(), userIds(joined), userIds(left));
    }

    private static List<UUID> userIds(Collection<DiningTableMember> members) {
        return members.stream().map(member -> member.getApplication().getUser()).filter(Objects::nonNull)
                .map(User::getId).distinct().toList();
    }

    private static List<UUID> memberIds(Collection<DiningTableMember> members) {
        return members.stream().map(DiningTableMember::getId).toList();
    }

    private static List<Application> applications(Collection<DiningTableMember> members) {
        return members.stream().map(DiningTableMember::getApplication).toList();
    }

    private static List<UUID> applicationIds(Collection<DiningTableMember> members) {
        return members.stream().map(member -> member.getApplication().getId()).toList();
    }

    private static List<UUID> applicationIdsOf(Collection<Application> applications) {
        return applications.stream().map(Application::getId).toList();
    }

    private static List<UUID> append(List<UUID> ids, UUID id) {
        List<UUID> result = new ArrayList<>(ids);
        result.add(id);
        return result;
    }
}
