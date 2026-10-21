package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminMatchingRuleService;
import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
import com.whatsuphouse.backend.domain.dining.enums.DiningRegion;
import com.whatsuphouse.backend.domain.dining.service.SessionVenueService;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.matching.dto.response.DiningTableListResponse;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchRunResponse;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.enums.MatchRunTrigger;
import com.whatsuphouse.backend.domain.matching.enums.UnassignedReason;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableMemberRepository;
import com.whatsuphouse.backend.domain.matching.repository.DiningTableRepository;
import com.whatsuphouse.backend.domain.matching.repository.MatchRunRepository;
import com.whatsuphouse.backend.domain.notification.event.DiningReallocatingEvent;
import com.whatsuphouse.backend.domain.payment.service.DepositService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 우연한 식탁 매칭 실행(rule-v2)과 회차 테이블 조회. (설계 4.1~4.5, KAN-345) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiningMatchService {

    private static final Set<DiningTableStatus> ACTIVE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED);
    private static final Set<DiningTableStatus> VISIBLE_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.PROPOSED, DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);
    // 이전 만남 페널티 기준: 실제로 같이 앉기로 확정됐거나 다녀온 테이블
    private static final Set<DiningTableStatus> MET_TABLE_STATUSES =
            EnumSet.of(DiningTableStatus.CONFIRMED, DiningTableStatus.DONE);
    private static final Set<MatchStatus> CANDIDATE_MATCH_STATUSES = EnumSet.of(MatchStatus.WAITING, MatchStatus.REALLOCATING);

    private final GatheringService gatheringService;
    private final AdminApplicationService adminApplicationService;
    private final AdminMatchingRuleService adminMatchingRuleService;
    private final SessionVenueService sessionVenueService;
    private final MatchExclusionProvider matchExclusionProvider;
    private final MatchingEngine matchingEngine;
    private final DiningTableRepository diningTableRepository;
    private final DiningTableMemberRepository diningTableMemberRepository;
    private final MatchRunRepository matchRunRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MatchResolutionService matchResolutionService;
    private final DepositService depositService;

    /**
     * 한 회차 규칙과 신청들의 매칭 프로필·관계를 한 번에 읽어 둔 평가기. 수동 조정·재조정이 여러 멤버 구성을
     * 비교·재검증할 때 쓴다(구성마다 DB를 다시 읽지 않는다). (KAN-347)
     */
    public record TableEvaluator(MatchingEngine engine, Map<UUID, MatchingEngine.Applicant> profiles,
                                 MatchingEngine.Rules rules, MatchingEngine.Relations relations) {

        public List<MatchingEngine.HardRule> violations(Collection<UUID> applicationIds) {
            return engine.violations(members(applicationIds), rules, relations);
        }

        public MatchingEngine.Score score(Collection<UUID> applicationIds) {
            return engine.score(members(applicationIds), rules, relations);
        }

        private List<MatchingEngine.Applicant> members(Collection<UUID> applicationIds) {
            return applicationIds.stream().map(profiles::get).toList();
        }
    }

    /**
     * 회차 매칭을 실행하고 MatchRun에 기록한다. 회차 행을 잠가 같은 회차의 실행을 직렬화한다.
     * sizeOverride는 v1 API(고정 그룹 인원) 호환용으로 최소 = 최대 인원을 강제한다. null이면 회차 값 → 매칭 규칙 기본값.
     */
    @Transactional
    public MatchRunResponse runMatch(UUID sessionId, MatchRunTrigger trigger, UUID triggeredUserId, Integer sizeOverride) {
        GatheringSession session = gatheringService.lockRandomTableSession(sessionId);
        GatheringSessionStatus sessionStatus = session.getEffectiveSessionStatus();
        if (sessionStatus == GatheringSessionStatus.CANCELLED || sessionStatus == GatheringSessionStatus.DONE) {
            throw new CustomException(ErrorCode.SESSION_NOT_MATCHABLE);
        }
        MatchingRuleSetting setting = adminMatchingRuleService.findMatchingRuleSetting();
        MatchRun run = matchRunRepository.save(
                MatchRun.start(sessionId, trigger, triggeredUserId, MatchingEngine.ALGORITHM_VERSION));

        // 재실행: 잠기지 않은 제안 테이블만 해체하고 멤버를 후보로 되돌린다. 확정·잠긴 테이블은 그대로 둔다.
        dissolveProposedTables(sessionId);

        // 후보: 이 회차를 희망했고, 결제 완료(CONFIRMED), 매칭 대기(WAITING|REALLOCATING, NULL=WAITING),
        // 참여 자격 APPROVED, 활성 테이블 없음, 보증금 대상이면 보증금 입금 확인(KAN-402). 입력 순서(신청 순)가 엔진의 결정적 동점 처리 기준이다.
        List<Application> eligible = adminApplicationService.listSessionApplications(sessionId).stream()
                .filter(a -> a.getStatus() == ApplicationStatus.CONFIRMED)
                .filter(a -> a.getMatchStatus() == null || CANDIDATE_MATCH_STATUSES.contains(a.getMatchStatus()))
                .filter(a -> a.getUser() != null && a.getUser().isApprovedForRandomTable())
                .toList();
        List<UUID> eligibleIds = eligible.stream().map(Application::getId).toList();
        Map<UUID, List<ApplicationCandidateSession>> wishes = adminApplicationService.findCandidateSessions(eligibleIds);
        Set<UUID> seated = findSeatedApplicationIds(eligibleIds);
        Set<UUID> unpaidDeposit = depositService.findUnsatisfiedApplicationIds(eligibleIds);
        List<Application> candidates = eligible.stream()
                .filter(a -> wishes.getOrDefault(a.getId(), List.of()).stream()
                        .anyMatch(wish -> wish.getSession().getId().equals(sessionId)))
                .filter(a -> !seated.contains(a.getId()))
                .filter(a -> !unpaidDeposit.contains(a.getId()))
                .sorted(Comparator.comparing(Application::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Application::getId))
                .toList();

        List<ApplicationAnswer> answers = adminApplicationService.findAnswers(
                candidates.stream().map(Application::getId).toList());
        List<MatchingEngine.Applicant> applicants = toApplicants(candidates, answers);
        MatchingEngine.Rules rules = rules(session, setting, answers, sizeOverride);
        if (rules.tableSizeMin() > rules.tableSizeMax()) {
            throw new CustomException(ErrorCode.INVALID_TABLE_SIZE_RANGE);
        }
        RegionMatch result = matchByRegion(candidates, applicants, rules, relations(applicants, null),
                setting.getMinTablesPerRegion());

        // 결과 테이블은 PROPOSED, 유예가 끝나면 자동 확정(KAN-346). 멤버는 확정 대기.
        Map<UUID, Application> byId = candidates.stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        int graceMinutes = session.getAutoConfirmGraceMinutes() != null
                ? session.getAutoConfirmGraceMinutes() : setting.getAutoConfirmGraceMinutes();
        LocalDateTime confirmAt = LocalDateTime.now().plusMinutes(graceMinutes);
        for (RegionTable regionTable : result.tables()) {
            MatchingEngine.TableResult tableResult = regionTable.result();
            DiningTable table = diningTableRepository.save(DiningTable.builder()
                    .session(session)
                    .matchRunId(run.getId())
                    .eventDate(session.getEventDate())
                    .region(regionTable.region().getLabel())
                    .groupSize(tableResult.seats().size())
                    .algorithmVersion(MatchingEngine.ALGORITHM_VERSION)
                    .groupScore(tableResult.score().value())
                    .scoreDetail(tableResult.score().detail())
                    .confirmAt(confirmAt)
                    .build());
            int seat = 1;
            for (MatchingEngine.Seat member : tableResult.seats()) {
                Application application = byId.get(member.applicationId());
                diningTableMemberRepository.save(DiningTableMember.builder()
                        .application(application)
                        .table(table)
                        .seatOrder(seat++)
                        .assignReason(member.reason())
                        .isManual(false)
                        .build());
                application.changeMatchStatus(MatchStatus.CONFIRM_PENDING);
            }
        }

        Set<UUID> pendingSessionIds = findPendingSessionIds(wishes, sessionId);
        List<MatchRun.Unassigned> unassigned = result.unassigned().stream()
                .map(u -> settleUnassigned(sessionId, byId.get(u.applicationId()), u, wishes, pendingSessionIds))
                .toList();
        run.finish(candidates.size(), result.tables().size(), result.splitCount(), result.mergeCount(),
                result.reallocatedCount(), unassigned);
        run.recordRegionResults(result.regionResults());
        return MatchRunResponse.from(run);
    }

    /** 회차 콘솔 테이블 탭. 해체되지 않은 테이블, 최신 실행의 미배정, 최신 실행 집계. */
    public DiningTableListResponse getTables(UUID sessionId) {
        gatheringService.findRandomTableSession(sessionId);
        List<DiningTable> tables = diningTableRepository
                .findBySession_IdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(sessionId, VISIBLE_TABLE_STATUSES);
        List<DiningTableMember> members = tables.isEmpty() ? List.of()
                : diningTableMemberRepository.findByTableIdsWithApplication(tables.stream().map(DiningTable::getId).toList());
        List<Application> memberApplications = members.stream().map(DiningTableMember::getApplication).toList();
        Map<UUID, MatchingEngine.Applicant> profiles = toApplicants(memberApplications,
                adminApplicationService.findAnswers(memberApplications.stream().map(Application::getId).toList()))
                .stream()
                .collect(Collectors.toMap(MatchingEngine.Applicant::applicationId, Function.identity(), (a, b) -> a));
        Map<UUID, List<DiningTableMember>> membersByTable = members.stream()
                .collect(Collectors.groupingBy(member -> member.getTable().getId()));

        List<DiningTableListResponse.TableView> tableViews = tables.stream()
                .map(table -> DiningTableListResponse.TableView.builder()
                        .id(table.getId())
                        .status(table.getStatus())
                        .region(table.getDisplayRegion())
                        .groupScore(table.getGroupScore())
                        .scoreDetail(table.getScoreDetail())
                        .confirmAt(table.getConfirmAt())
                        .locked(table.isLocked())
                        .venueId(table.getVenueId())
                        .chatRoomId(table.getChatRoomId())
                        .members(membersByTable.getOrDefault(table.getId(), List.of()).stream()
                                .map(member -> toMemberView(member, profiles.get(member.getApplication().getId())))
                                .toList())
                        .build())
                .toList();

        MatchRun lastRun = matchRunRepository.findFirstBySessionIdOrderByStartedAtDesc(sessionId).orElse(null);
        List<DiningTableListResponse.UnassignedView> unassigned = lastRun == null ? List.of() : toUnassignedViews(sessionId, lastRun);
        return DiningTableListResponse.builder()
                .tables(tableViews)
                .unassigned(unassigned)
                .lastRun(lastRun != null ? MatchRunResponse.from(lastRun) : null)
                .regionResults(lastRun == null ? List.of() : lastRun.getRegionResults().stream()
                        .map(r -> DiningTableListResponse.RegionResultView.builder()
                                .region(r.region())
                                .candidateCount(r.candidateCount())
                                .tableCount(r.tableCount())
                                .adopted(r.adopted())
                                .build())
                        .toList())
                .build();
    }

    /** 멤버 구성이 바뀐 테이블의 점수를 다시 계산한다(v1 수동 조정). */
    @Transactional
    public void rescoreTable(DiningTable table) {
        List<Application> applications = diningTableMemberRepository.findByTableIdWithApplication(table.getId()).stream()
                .map(DiningTableMember::getApplication)
                .toList();
        if (applications.isEmpty()) {
            table.updateScore(BigDecimal.ZERO, null);
            return;
        }
        List<ApplicationAnswer> answers = adminApplicationService.findAnswers(
                applications.stream().map(Application::getId).toList());
        List<MatchingEngine.Applicant> applicants = toApplicants(applications, answers);
        MatchingEngine.Score score = matchingEngine.score(applicants,
                rules(table.getSession(), adminMatchingRuleService.findMatchingRuleSetting(), answers, null),
                relations(applicants, table.getId()));
        table.updateScore(score.value(), score.detail());
    }

    /**
     * 이 회차에서 applications로 만들 수 있는 멤버 구성을 평가하는 평가기. tableId는 평가할 테이블로,
     * 그 테이블에 함께 앉은 이력은 "이전 만남" 페널티에서 뺀다(없으면 null). (KAN-347)
     */
    public TableEvaluator evaluator(GatheringSession session, Collection<Application> applications, UUID tableId) {
        Map<UUID, Application> distinct = new LinkedHashMap<>();
        applications.forEach(application -> distinct.putIfAbsent(application.getId(), application));
        List<ApplicationAnswer> answers = adminApplicationService.findAnswers(distinct.keySet());
        List<MatchingEngine.Applicant> applicants = toApplicants(List.copyOf(distinct.values()), answers);
        return new TableEvaluator(matchingEngine,
                applicants.stream().collect(Collectors.toMap(MatchingEngine.Applicant::applicationId, Function.identity())),
                rules(session, adminMatchingRuleService.findMatchingRuleSetting(), answers, null),
                relations(applicants, tableId));
    }

    /**
     * 테이블 멤버 구성이 하드 조건(인원·나이 차·제외 관계)을 지키고 멤버 전원이 테이블 지역을 희망하는지. 자동 확정 직전 최종 검증. (KAN-346)
     * 지역이 없는(지역 매칭 전) 테이블은 지역 조건을 보지 않는다. (KAN-431)
     */
    public boolean satisfiesHardConditions(DiningTable table, List<Application> applications) {
        if (applications.isEmpty() || !applications.stream().allMatch(a -> wantsRegion(a, table.getDisplayRegion()))) {
            return false;
        }
        List<ApplicationAnswer> answers = adminApplicationService.findAnswers(
                applications.stream().map(Application::getId).toList());
        List<MatchingEngine.Applicant> applicants = toApplicants(applications, answers);
        return matchingEngine.satisfiesHardConditions(applicants,
                rules(table.getSession(), adminMatchingRuleService.findMatchingRuleSetting(), answers, null),
                relations(applicants, table.getId()));
    }

    /** 신청 ID → 매칭 프로필(표준 질문 답). 참가자 테이블 상세의 멤버 소개용. (KAN-346) */
    public Map<UUID, MatchingEngine.Applicant> findProfiles(List<Application> applications) {
        if (applications.isEmpty()) {
            return Map.of();
        }
        return toApplicants(applications,
                adminApplicationService.findAnswers(applications.stream().map(Application::getId).toList()))
                .stream()
                .collect(Collectors.toMap(MatchingEngine.Applicant::applicationId, Function.identity(), (a, b) -> a));
    }

    /** 신청이 지역(라벨)을 희망하는지. 지역이 없으면(지역 매칭 전 테이블) 통과. 수동 조정·재조정·확정 재검증이 같이 쓴다. (KAN-431) */
    static boolean wantsRegion(Application application, String region) {
        return region == null || application.getCandidateRegions().stream().anyMatch(r -> r.getLabel().equals(region));
    }

    // ── 실행 단계 ────────────────────────────────────────────────────────────

    /** 회차×지역 매칭 결과. tables는 채택된 지역의 테이블(지역 처리 순), unassigned는 끝까지 못 앉은 후보(신청 순). */
    private record RegionMatch(List<RegionTable> tables, List<MatchRun.Unassigned> unassigned,
                               List<MatchRun.RegionResult> regionResults,
                               int splitCount, int mergeCount, int reallocatedCount) {
    }

    private record RegionTable(DiningRegion region, MatchingEngine.TableResult result) {
    }

    /**
     * 회차×지역 매칭(KAN-431). 지역을 후보 수 많은 순 → 그 지역만 고른 사람 많은 순 → 선언 순으로 돌며
     * "남은 후보 중 이 지역 희망자"를 엔진에 넣는다. 테이블이 minTablesPerRegion개 이상이면 채택해 앉은 사람을 남은 후보에서 빼고
     * (엔진 미배정자는 남은 후보로 돌아간다), 미만이면 그 지역 결과를 버린다(아무도 빼지 않는다). 무작위가 없어 같은 입력이면 같은 결과다.
     * 끝까지 남은 사람의 사유: 채택된 지역에서 받은 하드 조건 사유(나이 차·제외 관계) → 결과가 버려진 지역에 있었으면 REGION_TOO_SMALL
     * → 그 밖에는 NOT_ENOUGH_PEOPLE.
     * ponytail: 지역 순서 그리디라 여러 지역을 고른 사람이 앞 지역에 먼저 흡수된다. 지역 간 최적 분배가 필요해지면
     * "버려진 지역에 대해 앞 지역의 다지역 희망자를 옮겨 보는" 2차 패스를 추가한다.
     */
    private RegionMatch matchByRegion(List<Application> candidates, List<MatchingEngine.Applicant> applicants,
                                      MatchingEngine.Rules rules, MatchingEngine.Relations relations, int minTablesPerRegion) {
        Map<UUID, Set<DiningRegion>> wanted = candidates.stream()
                .collect(Collectors.toMap(Application::getId, Application::getCandidateRegions));
        Map<DiningRegion, Long> candidateCount = new EnumMap<>(DiningRegion.class);
        Map<DiningRegion, Long> onlyCount = new EnumMap<>(DiningRegion.class);
        for (DiningRegion region : DiningRegion.values()) {
            candidateCount.put(region, wanted.values().stream().filter(r -> r.contains(region)).count());
            onlyCount.put(region, wanted.values().stream().filter(r -> r.size() == 1 && r.contains(region)).count());
        }
        List<DiningRegion> order = Arrays.stream(DiningRegion.values())
                .sorted(Comparator.comparing((DiningRegion r) -> candidateCount.get(r), Comparator.reverseOrder())
                        .thenComparing(r -> onlyCount.get(r), Comparator.reverseOrder())
                        .thenComparing(Comparator.naturalOrder()))
                .toList();

        Set<UUID> remaining = new LinkedHashSet<>();
        candidates.forEach(a -> remaining.add(a.getId()));
        Map<UUID, UnassignedReason> hardReasons = new HashMap<>();
        Set<UUID> inDiscardedRegion = new HashSet<>();
        List<RegionTable> tables = new ArrayList<>();
        List<MatchRun.RegionResult> regionResults = new ArrayList<>();
        int splitCount = 0;
        int mergeCount = 0;
        int reallocatedCount = 0;
        for (DiningRegion region : order) {
            List<MatchingEngine.Applicant> pool = applicants.stream()
                    .filter(a -> remaining.contains(a.applicationId()) && wanted.get(a.applicationId()).contains(region))
                    .toList();
            MatchingEngine.Result result = pool.isEmpty() ? null : matchingEngine.match(pool, rules, relations);
            int tableCount = result == null ? 0 : result.tables().size();
            boolean adopted = result != null && tableCount >= minTablesPerRegion;
            regionResults.add(new MatchRun.RegionResult(region.getLabel(), pool.size(), tableCount, adopted));
            if (!adopted) {
                pool.forEach(a -> inDiscardedRegion.add(a.applicationId()));
                continue;
            }
            for (MatchingEngine.TableResult table : result.tables()) {
                tables.add(new RegionTable(region, table));
                table.seats().forEach(seat -> remaining.remove(seat.applicationId()));
            }
            result.unassigned().stream()
                    .filter(u -> u.reason() != UnassignedReason.NOT_ENOUGH_PEOPLE)
                    .forEach(u -> hardReasons.put(u.applicationId(), u.reason()));
            splitCount += result.splitCount();
            mergeCount += result.mergeCount();
            reallocatedCount += result.reallocatedCount();
        }

        List<MatchRun.Unassigned> unassigned = remaining.stream()
                .map(id -> new MatchRun.Unassigned(id, hardReasons.getOrDefault(id, inDiscardedRegion.contains(id)
                        ? UnassignedReason.REGION_TOO_SMALL : UnassignedReason.NOT_ENOUGH_PEOPLE)))
                .toList();
        return new RegionMatch(tables, unassigned, regionResults, splitCount, mergeCount, reallocatedCount);
    }

    private void dissolveProposedTables(UUID sessionId) {
        List<DiningTable> tables = diningTableRepository
                .findBySession_IdAndStatusAndLockedFalseAndDeletedAtIsNull(sessionId, DiningTableStatus.PROPOSED);
        if (tables.isEmpty()) {
            return;
        }
        diningTableMemberRepository.findByTableIdsWithApplication(tables.stream().map(DiningTable::getId).toList())
                .forEach(member -> member.getApplication().changeMatchStatus(MatchStatus.WAITING));
        for (DiningTable table : tables) {
            if (table.getVenueId() != null) {
                sessionVenueService.releaseTable(sessionId, table.getVenueId());
            }
            table.dissolve();
        }
        // 이어지는 후보 조회(활성 테이블 여부)가 해체 결과를 보도록 먼저 반영한다.
        diningTableRepository.flush();
    }

    private Set<UUID> findSeatedApplicationIds(Collection<UUID> applicationIds) {
        return applicationIds.isEmpty() ? Set.of()
                : Set.copyOf(diningTableMemberRepository.findApplicationIdsByTableStatusIn(applicationIds, ACTIVE_TABLE_STATUSES));
    }

    // 아직 매칭을 기다리는 다른 희망 회차: 이 회차가 아니고, 모집 중(OPEN)이며, 매칭 실행 기록이 없는 회차.
    private Set<UUID> findPendingSessionIds(Map<UUID, List<ApplicationCandidateSession>> wishes, UUID sessionId) {
        Set<UUID> openSessionIds = wishes.values().stream()
                .flatMap(List::stream)
                .map(ApplicationCandidateSession::getSession)
                .filter(s -> !s.getId().equals(sessionId) && s.getDeletedAt() == null
                        && s.getEffectiveSessionStatus() == GatheringSessionStatus.OPEN)
                .map(GatheringSession::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (!openSessionIds.isEmpty()) {
            openSessionIds.removeAll(matchRunRepository.findSessionIdsIn(openSessionIds));
        }
        return openSessionIds;
    }

    // 다음 희망 회차가 남아 있으면 REALLOCATING(그 회차 실행 때 후보로 들어간다), 마지막이면 ALTERNATIVE_OFFERED.
    // REALLOCATING으로 새로 바뀔 때만 알림 이벤트를 낸다(같은 회차 재실행으로 다시 미배정돼도 한 번).
    private MatchRun.Unassigned settleUnassigned(UUID sessionId, Application application, MatchRun.Unassigned unassigned,
                                                 Map<UUID, List<ApplicationCandidateSession>> wishes,
                                                 Set<UUID> pendingSessionIds) {
        boolean hasNextSession = wishes.getOrDefault(application.getId(), List.of()).stream()
                .anyMatch(wish -> pendingSessionIds.contains(wish.getSession().getId()));
        if (hasNextSession) {
            if (application.getMatchStatus() != MatchStatus.REALLOCATING && application.getUser() != null) {
                eventPublisher.publishEvent(new DiningReallocatingEvent(application.getId(), sessionId, application.getUser().getId()));
            }
            application.changeMatchStatus(MatchStatus.REALLOCATING);
            return new MatchRun.Unassigned(application.getId(), UnassignedReason.NEXT_SESSION_WAITING);
        }
        // 대체 회차를 제안한다. 제안할 회차가 없으면 해결 선택 서비스가 NO_MATCH로 바꾼다(KEEP_TICKET/REFUND만).
        application.changeMatchStatus(MatchStatus.ALTERNATIVE_OFFERED);
        matchResolutionService.offerResolution(application, wishes.getOrDefault(application.getId(), List.of()).stream()
                .map(wish -> wish.getSession().getId())
                .toList());
        return unassigned;
    }

    // ── 엔진 입력 ────────────────────────────────────────────────────────────

    // 회차 값이 있으면 회차 값, 없으면 매칭 규칙 기본값. 커스텀 질문은 답변이 달린 매칭 질문(reserved_key 없음)에서 모은다.
    private static MatchingEngine.Rules rules(GatheringSession session, MatchingRuleSetting setting,
                                              List<ApplicationAnswer> answers, Integer sizeOverride) {
        Map<String, MatchingEngine.CustomField> customFields = new TreeMap<>();
        for (ApplicationAnswer answer : answers) {
            FormQuestion question = answer.getQuestion();
            if (isCustomMatchingQuestion(question)) {
                customFields.putIfAbsent(question.getQuestionKey(), new MatchingEngine.CustomField(
                        question.getQuestionKey(), question.getMatchingStrategy(),
                        question.getMatchingWeight() != null ? question.getMatchingWeight().doubleValue() : 1.0));
            }
        }
        return new MatchingEngine.Rules(
                sizeOverride != null ? sizeOverride : Objects.requireNonNullElse(session.getTableSizeMin(), setting.getTableSizeMin()),
                sizeOverride != null ? sizeOverride : Objects.requireNonNullElse(session.getTableSizeMax(), setting.getTableSizeMax()),
                Objects.requireNonNullElse(session.getMaxAgeGap(), setting.getMaxAgeGap()),
                setting.getWeights(),
                List.copyOf(customFields.values()));
    }

    private static boolean isCustomMatchingQuestion(FormQuestion question) {
        return question.getDeletedAt() == null && question.getReservedKey() == null
                && question.isMatchingField() && question.getMatchingStrategy() != null;
    }

    private static List<MatchingEngine.Applicant> toApplicants(List<Application> applications, List<ApplicationAnswer> answers) {
        Map<UUID, Map<ReservedQuestionKey, Object>> reserved = new HashMap<>();
        Map<UUID, Map<String, Object>> custom = new HashMap<>();
        for (ApplicationAnswer answer : answers) {
            FormQuestion question = answer.getQuestion();
            UUID applicationId = answer.getApplication().getId();
            Object value = answer.getValue() != null ? answer.getValue().get("value") : null;
            if (question.getDeletedAt() == null && question.getReservedKey() != null) {
                reserved.computeIfAbsent(applicationId, id -> new EnumMap<>(ReservedQuestionKey.class))
                        .put(question.getReservedKey(), value);
            } else if (isCustomMatchingQuestion(question)) {
                custom.computeIfAbsent(applicationId, id -> new HashMap<>()).put(question.getQuestionKey(), value);
            }
        }
        return applications.stream()
                .map(application -> {
                    Map<ReservedQuestionKey, Object> r = reserved.getOrDefault(application.getId(), Map.of());
                    User user = application.getUser();
                    String mbti = text(r.get(ReservedQuestionKey.MBTI));
                    return new MatchingEngine.Applicant(
                            application.getId(),
                            user != null ? user.getId() : null,
                            year(r.get(ReservedQuestionKey.BIRTH_YEAR)),
                            text(r.get(ReservedQuestionKey.GENDER)),
                            mbti != null ? mbti.toUpperCase() : null,
                            values(r.get(ReservedQuestionKey.INTERESTS)),
                            values(r.get(ReservedQuestionKey.MY_STYLE)),
                            values(r.get(ReservedQuestionKey.WANTED_STYLE)),
                            user != null ? Job.fromCode(user.getJob()).map(job -> job.getCategory().name()).orElse(null) : null,
                            custom.getOrDefault(application.getId(), Map.of()));
                })
                .toList();
    }

    // excludeTableId: 점수를 다시 매기는 테이블 자신은 "이전 만남"에서 뺀다.
    private MatchingEngine.Relations relations(List<MatchingEngine.Applicant> applicants, UUID excludeTableId) {
        List<UUID> userIds = applicants.stream().map(MatchingEngine.Applicant::userId).filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return MatchingEngine.Relations.none();
        }
        Map<UUID, Set<UUID>> metBefore = new HashMap<>();
        diningTableMemberRepository.findUserPairsByTableStatusIn(userIds, MET_TABLE_STATUSES, excludeTableId)
                .forEach(p -> metBefore.computeIfAbsent(p.getUserId(), id -> new HashSet<>()).add(p.getOtherUserId()));
        return new MatchingEngine.Relations(matchExclusionProvider.findExcludedPairs(userIds), metBefore,
                matchExclusionProvider.findAgainPairs(userIds));
    }

    // ── 응답 ────────────────────────────────────────────────────────────────

    private static DiningTableListResponse.MemberView toMemberView(DiningTableMember member, MatchingEngine.Applicant profile) {
        User user = member.getApplication().getUser();
        return DiningTableListResponse.MemberView.builder()
                .memberId(member.getId())
                .applicationId(member.getApplication().getId())
                .userId(user != null ? user.getId() : null)
                .nickname(user != null ? user.getNickname() : member.getApplication().getName())
                .birthYear(profile.birthYear())
                .gender(profile.gender())
                .mbti(profile.mbti())
                .assignReason(member.getAssignReason())
                .isManual(member.isManual())
                .build();
    }

    // 최신 실행의 미배정 중 이후 활성 테이블에 들어간 신청(수동 배정·다른 회차 배정)은 뺀다.
    private List<DiningTableListResponse.UnassignedView> toUnassignedViews(UUID sessionId, MatchRun run) {
        List<UUID> ids = run.getUnassignedReasons().stream().map(MatchRun.Unassigned::applicationId).toList();
        Set<UUID> seated = findSeatedApplicationIds(ids);
        Map<UUID, Application> applications = adminApplicationService.listSessionApplications(sessionId).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
        return run.getUnassignedReasons().stream()
                .filter(u -> !seated.contains(u.applicationId()))
                .map(u -> {
                    Application application = applications.get(u.applicationId());
                    String nickname = application == null ? null
                            : application.getUser() != null ? application.getUser().getNickname() : application.getName();
                    return DiningTableListResponse.UnassignedView.builder()
                            .applicationId(u.applicationId())
                            .nickname(nickname)
                            .reason(u.reason())
                            .build();
                })
                .toList();
    }

    // ── 답변 값 변환 ──────────────────────────────────────────────────────────

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static Integer year(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 답변 값(단일 값 또는 목록)을 문자열 집합으로. 식당 가격대 선호(DiningConfirmService)도 쓴다.
    static Set<String> values(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(Objects::nonNull).map(Object::toString).forEach(result::add);
        } else if (text(value) != null) {
            result.add(text(value));
        }
        return result;
    }
}
