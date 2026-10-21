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
                        .matchedAt(table.getMatchedAt())
                        .confirmedAt(table.getConfirmedAt())
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
     * 회차×지역 매칭(KAN-431), 희망 지역 지망 순위 반영.
     * 열 지역 조합(누군가 희망한 지역들의 부분집합, 최대 2^4=16개)을 모두 따져 본다. 조합마다 사람을 그 조합 안에서 가장 높은
     * 지망 지역에 넣고 지역마다 엔진을 돌려, 모든 지역이 minTablesPerRegion개 이상 테이블을 만들 때만 유효한 조합으로 본다.
     * 유효한 조합 중 ① 앉는 사람이 많은 것 ② 앉은 사람들의 지망 순위 합이 작은 것 ③ 선언 순서가 앞선 지역 조합 순으로 고른다.
     * 같은 (지역, 인원) 풀은 한 번만 엔진에 넣는다. 무작위가 없어 같은 입력이면 같은 결과다.
     * 고른 조합에 마무리 패스(finishUnseated)를 한 번 돌려, 채택 지역에서 엔진이 못 앉힌 사람을 다음 지망 채택 지역에 넣어 본다.
     * 끝까지 남은 사람의 사유: 채택된 지역에서 받은 하드 조건 사유(나이 차·제외 관계) → 희망 지역이 하나도 안 열렸으면
     * REGION_TOO_SMALL → 그 밖에는 NOT_ENOUGH_PEOPLE.
     * ponytail: 지역이 4곳보다 많아지면 조합 수가 2^n 이라 탐욕 방식으로 바꿔야 한다.
     */
    private RegionMatch matchByRegion(List<Application> candidates, List<MatchingEngine.Applicant> applicants,
                                      MatchingEngine.Rules rules, MatchingEngine.Relations relations, int minTablesPerRegion) {
        Map<UUID, List<DiningRegion>> wanted = candidates.stream()
                .collect(Collectors.toMap(Application::getId, Application::getCandidateRegions));
        List<DiningRegion> universe = Arrays.stream(DiningRegion.values())
                .filter(region -> wanted.values().stream().anyMatch(w -> w.contains(region)))
                .toList();
        Map<List<UUID>, MatchingEngine.Result> engineCache = new HashMap<>();

        RegionPlan best = null;
        for (int mask = 0; mask < (1 << universe.size()); mask++) {
            Set<DiningRegion> open = EnumSet.noneOf(DiningRegion.class);
            for (int i = 0; i < universe.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    open.add(universe.get(i));
                }
            }
            RegionPlan plan = planFor(open, applicants, wanted, rules, relations, engineCache);
            boolean valid = plan.results().values().stream().allMatch(r -> r.tables().size() >= minTablesPerRegion)
                    && plan.results().keySet().equals(open);
            if (valid && (best == null || plan.isBetterThan(best))) {
                best = plan;
            }
        }
        // 지역마다 "모두 열었을 때" 풀·테이블 수 — 채택되지 않은 지역의 결과 표시에 쓴다
        RegionPlan allOpen = planFor(universe.isEmpty() ? EnumSet.noneOf(DiningRegion.class) : EnumSet.copyOf(universe),
                applicants, wanted, rules, relations, engineCache);
        RegionPlan finished = best == null ? null
                : finishUnseated(best, applicants, wanted, rules, relations, minTablesPerRegion, engineCache);
        return adoptRegions(candidates, wanted, finished, allOpen);
    }

    /**
     * 같은 날짜 다른 지역 마무리 패스(STA-03, KAN-436). 채택 지역에서 엔진이 못 앉힌 사람(하드 조건 사유 포함)을 신청 순으로,
     * 그 사람의 다음 지망 지역 중 채택된 지역에 지망 순으로 한 번씩 넣어 그 지역만 엔진을 다시 돌린다.
     * 다시 돌린 결과가 그 지역 테이블 수 ≥ minTablesPerRegion, 그 지역에 이미 앉아 있던 사람 전원 유지, 넣은 사람 착석일 때만
     * 그 지역 결과를 바꾸고(넣은 사람은 원래 지역 풀에서 뺀다), 아니면 원래 결과를 둔다.
     * ponytail: 사람마다 탐욕으로 한 번씩만 본다. 앞사람을 옮긴 뒤 뒷사람 기회가 달라져도 다시 돌지 않는다(모두 다시 보려면 바뀌지 않을 때까지 반복).
     */
    private RegionPlan finishUnseated(RegionPlan best, List<MatchingEngine.Applicant> applicants,
                                      Map<UUID, List<DiningRegion>> wanted, MatchingEngine.Rules rules,
                                      MatchingEngine.Relations relations, int minTablesPerRegion,
                                      Map<List<UUID>, MatchingEngine.Result> engineCache) {
        Map<DiningRegion, List<MatchingEngine.Applicant>> pools = new EnumMap<>(DiningRegion.class);
        pools.putAll(best.pools());
        Map<DiningRegion, MatchingEngine.Result> results = new EnumMap<>(DiningRegion.class);
        results.putAll(best.results());
        for (MatchingEngine.Applicant person : applicants) {
            UUID id = person.applicationId();
            DiningRegion home = wanted.get(id).stream().filter(best.open()::contains).findFirst().orElse(null);
            if (home == null || seatedIds(results.get(home)).contains(id)) {
                continue;
            }
            for (DiningRegion next : wanted.get(id)) {
                if (next == home || !best.open().contains(next)) {
                    continue;
                }
                Set<UUID> poolIds = new HashSet<>(ids(pools.get(next)));
                poolIds.add(id);
                // 엔진 동점 처리 기준(입력 순서)을 지키려고 신청 순으로 다시 세운다
                List<MatchingEngine.Applicant> pool = applicants.stream().filter(a -> poolIds.contains(a.applicationId())).toList();
                MatchingEngine.Result retry = engineCache.computeIfAbsent(ids(pool), k -> matchingEngine.match(pool, rules, relations));
                Set<UUID> retrySeated = seatedIds(retry);
                if (retry.tables().size() >= minTablesPerRegion && retrySeated.contains(id)
                        && retrySeated.containsAll(seatedIds(results.get(next)))) {
                    pools.put(next, pool);
                    results.put(next, retry);
                    pools.put(home, pools.get(home).stream().filter(a -> !a.applicationId().equals(id)).toList());
                    break;
                }
            }
        }
        return new RegionPlan(best.open(), pools, results, best.seated(), best.rankSum(), best.order());
    }

    private static List<UUID> ids(List<MatchingEngine.Applicant> applicants) {
        return applicants.stream().map(MatchingEngine.Applicant::applicationId).toList();
    }

    private static Set<UUID> seatedIds(MatchingEngine.Result result) {
        return result.tables().stream()
                .flatMap(table -> table.seats().stream())
                .map(MatchingEngine.Seat::applicationId)
                .collect(Collectors.toSet());
    }

    // 열린 지역 조합 하나의 배정 결과. seated = 앉은 사람 수, rankSum = 앉은 사람들의 지망 순위 합(1지망=1)
    private record RegionPlan(Set<DiningRegion> open, Map<DiningRegion, List<MatchingEngine.Applicant>> pools,
                              Map<DiningRegion, MatchingEngine.Result> results, int seated, int rankSum, int order) {
        boolean isBetterThan(RegionPlan other) {
            if (seated != other.seated) return seated > other.seated;
            if (rankSum != other.rankSum) return rankSum < other.rankSum;
            return order < other.order;
        }
    }

    private RegionPlan planFor(Set<DiningRegion> open, List<MatchingEngine.Applicant> applicants,
                               Map<UUID, List<DiningRegion>> wanted, MatchingEngine.Rules rules,
                               MatchingEngine.Relations relations, Map<List<UUID>, MatchingEngine.Result> engineCache) {
        Map<DiningRegion, List<MatchingEngine.Applicant>> pools = new EnumMap<>(DiningRegion.class);
        for (MatchingEngine.Applicant applicant : applicants) {
            wanted.get(applicant.applicationId()).stream().filter(open::contains).findFirst()
                    .ifPresent(region -> pools.computeIfAbsent(region, r -> new ArrayList<>()).add(applicant));
        }
        Map<DiningRegion, MatchingEngine.Result> results = new EnumMap<>(DiningRegion.class);
        int seated = 0;
        int rankSum = 0;
        for (Map.Entry<DiningRegion, List<MatchingEngine.Applicant>> entry : pools.entrySet()) {
            List<UUID> key = entry.getValue().stream().map(MatchingEngine.Applicant::applicationId).toList();
            MatchingEngine.Result result = engineCache.computeIfAbsent(key, k -> matchingEngine.match(entry.getValue(), rules, relations));
            results.put(entry.getKey(), result);
            for (MatchingEngine.TableResult table : result.tables()) {
                for (MatchingEngine.Seat seat : table.seats()) {
                    seated++;
                    rankSum += wanted.get(seat.applicationId()).indexOf(entry.getKey()) + 1;
                }
            }
        }
        int order = open.stream().mapToInt(region -> 1 << region.ordinal()).sum();
        return new RegionPlan(open, pools, results, seated, rankSum, order);
    }

    private RegionMatch adoptRegions(List<Application> candidates, Map<UUID, List<DiningRegion>> wanted,
                                     RegionPlan best, RegionPlan allOpen) {
        Set<UUID> remaining = new LinkedHashSet<>();
        candidates.forEach(a -> remaining.add(a.getId()));
        Map<UUID, UnassignedReason> hardReasons = new HashMap<>();
        List<RegionTable> tables = new ArrayList<>();
        List<MatchRun.RegionResult> regionResults = new ArrayList<>();
        int splitCount = 0;
        int mergeCount = 0;
        int reallocatedCount = 0;
        Set<DiningRegion> adoptedRegions = best == null ? Set.of() : best.open();
        for (DiningRegion region : DiningRegion.values()) {
            if (!adoptedRegions.contains(region)) {
                if (allOpen.pools().containsKey(region)) {
                    regionResults.add(new MatchRun.RegionResult(region.getLabel(), allOpen.pools().get(region).size(),
                            allOpen.results().get(region).tables().size(), false));
                }
                continue;
            }
            MatchingEngine.Result result = best.results().get(region);
            regionResults.add(new MatchRun.RegionResult(region.getLabel(), best.pools().get(region).size(), result.tables().size(), true));
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
                .map(id -> new MatchRun.Unassigned(id, hardReasons.getOrDefault(id,
                        wanted.get(id).stream().noneMatch(adoptedRegions::contains)
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
                .reallocationCount(member.getApplication().getReallocationCount())
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
                            .reallocationCount(application == null ? 0 : application.getReallocationCount())
                            .failedAt(run.getFinishedAt())
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
