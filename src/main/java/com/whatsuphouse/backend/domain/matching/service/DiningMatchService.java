package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.service.AdminMatchingRuleService;
import com.whatsuphouse.backend.domain.dining.entity.MatchingRuleSetting;
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
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final MatchResolutionService matchResolutionService;

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
        // 참여 자격 APPROVED, 활성 테이블 없음. 입력 순서(신청 순)가 엔진의 결정적 동점 처리 기준이다.
        List<Application> eligible = adminApplicationService.listSessionApplications(sessionId).stream()
                .filter(a -> a.getStatus() == ApplicationStatus.CONFIRMED)
                .filter(a -> a.getMatchStatus() == null || CANDIDATE_MATCH_STATUSES.contains(a.getMatchStatus()))
                .filter(a -> a.getUser() != null && a.getUser().isApprovedForRandomTable())
                .toList();
        List<UUID> eligibleIds = eligible.stream().map(Application::getId).toList();
        Map<UUID, List<ApplicationCandidateSession>> wishes = adminApplicationService.findCandidateSessions(eligibleIds);
        Set<UUID> seated = findSeatedApplicationIds(eligibleIds);
        List<Application> candidates = eligible.stream()
                .filter(a -> wishes.getOrDefault(a.getId(), List.of()).stream()
                        .anyMatch(wish -> wish.getSession().getId().equals(sessionId)))
                .filter(a -> !seated.contains(a.getId()))
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
        MatchingEngine.Result result = matchingEngine.match(applicants, rules, relations(applicants, null));

        // 결과 테이블은 PROPOSED, 유예가 끝나면 자동 확정(KAN-346). 멤버는 확정 대기.
        Map<UUID, Application> byId = candidates.stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        int graceMinutes = session.getAutoConfirmGraceMinutes() != null
                ? session.getAutoConfirmGraceMinutes() : setting.getAutoConfirmGraceMinutes();
        LocalDateTime confirmAt = LocalDateTime.now().plusMinutes(graceMinutes);
        for (MatchingEngine.TableResult tableResult : result.tables()) {
            DiningTable table = diningTableRepository.save(DiningTable.builder()
                    .session(session)
                    .matchRunId(run.getId())
                    .eventDate(session.getEventDate())
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
                .map(u -> settleUnassigned(byId.get(u.applicationId()), u, wishes, pendingSessionIds))
                .toList();
        run.finish(candidates.size(), result.tables().size(), result.splitCount(), result.mergeCount(),
                result.reallocatedCount(), unassigned);
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
                        .groupScore(table.getGroupScore())
                        .scoreDetail(table.getScoreDetail())
                        .confirmAt(table.getConfirmAt())
                        .locked(table.isLocked())
                        .venueId(table.getVenueId())
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

    /** 테이블 멤버 구성이 하드 조건(인원·나이 차·제외 관계)을 지키는지. 자동 확정 직전 최종 검증. (KAN-346) */
    public boolean satisfiesHardConditions(DiningTable table, List<Application> applications) {
        if (applications.isEmpty()) {
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

    // ── 실행 단계 ────────────────────────────────────────────────────────────

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
    private MatchRun.Unassigned settleUnassigned(Application application, MatchRun.Unassigned unassigned,
                                                 Map<UUID, List<ApplicationCandidateSession>> wishes,
                                                 Set<UUID> pendingSessionIds) {
        boolean hasNextSession = wishes.getOrDefault(application.getId(), List.of()).stream()
                .anyMatch(wish -> pendingSessionIds.contains(wish.getSession().getId()));
        if (hasNextSession) {
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
                Objects.requireNonNullElse(session.getMinGroupScore(), setting.getMinGroupScore()).doubleValue(),
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

    private static Set<String> values(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(Objects::nonNull).map(Object::toString).forEach(result::add);
        } else if (text(value) != null) {
            result.add(text(value));
        }
        return result;
    }
}
