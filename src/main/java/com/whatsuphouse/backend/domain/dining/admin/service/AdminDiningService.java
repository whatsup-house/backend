package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningApplicantResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningDashboardResponse;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.enums.ReservedQuestionKey;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingResultResponse;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.service.DiningAttendanceService;
import com.whatsuphouse.backend.domain.matching.service.MatchExclusionProvider;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 우연한 식탁 운영 대시보드·회차 신청자 표·CSV 내보내기. (KAN-348) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminDiningService {

    // 성별·MBTI는 질문 키로 읽는다(표준 질문 GENDER·MBTI의 질문 키와 같다). 나이는 표준 질문 출생연도(BIRTH_YEAR)로 계산한다. (KAN-388)
    private static final String GENDER_QUESTION_KEY = "gender";
    private static final String MBTI_QUESTION_KEY = "mbti";
    private static final Set<String> PROFILE_QUESTION_KEYS = Set.of(GENDER_QUESTION_KEY, MBTI_QUESTION_KEY);
    private static final int MIN_BIRTH_YEAR = 1900;

    private static final Set<MatchStatus> WAITING_MATCH_STATUSES = Set.of(MatchStatus.WAITING, MatchStatus.REALLOCATING);
    private static final Set<ApplicationStatus> CLOSED_APPLICATION_STATUSES =
            Set.of(ApplicationStatus.REJECTED, ApplicationStatus.CANCELLED);

    private static final List<String> APPLICANT_CSV_HEADER = List.of(
            "신청 ID", "이름", "나이", "성별", "MBTI", "신청 상태", "결제", "매칭 상태", "희망 회차", "참가 횟수", "제외 관계");
    private static final List<String> TABLE_CSV_HEADER = List.of(
            "테이블", "테이블 ID", "상태", "그룹 점수", "인원", "식당", "좌석", "신청 ID", "이름");

    private final GatheringService gatheringService;
    private final AdminApplicationService adminApplicationService;
    private final MatchingService matchingService;
    private final DiningAttendanceService diningAttendanceService;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final VenueRepository venueRepository;
    private final MatchExclusionProvider matchExclusionProvider;

    public DiningDashboardResponse getDashboard() {
        List<GatheringSession> sessions = gatheringService.listUpcomingSessions(GatheringType.RANDOM_TABLE);
        List<UUID> sessionIds = sessions.stream().map(GatheringSession::getId).toList();
        Map<UUID, Map<DiningTableStatus, Long>> tableCounts = matchingService.countTablesBySessionIds(sessionIds);
        Set<UUID> matchRunSessionIds = matchingService.findSessionIdsWithMatchRun(sessionIds);
        Map<UUID, Long> openExceptions = sessionIds.isEmpty() ? Map.of()
                : exceptionCaseRepository.countBySessionIdsAndStatus(sessionIds, ExceptionCaseStatus.OPEN).stream()
                .collect(Collectors.toMap(ExceptionCaseRepository.SessionCountProjection::getSessionId,
                        ExceptionCaseRepository.SessionCountProjection::getCount));

        // 여러 회차를 희망한 신청은 회차 카드마다 세지만 타일에서는 한 번만 센다.
        Map<UUID, Application> distinctApplications = new LinkedHashMap<>();
        List<DiningDashboardResponse.SessionCard> cards = new ArrayList<>();
        for (GatheringSession session : sessions) {
            // ponytail: 회차마다 신청 조회 1회(N회). 다가오는 우연한 식탁 회차는 몇 개뿐이라 충분, 늘면 회차 ID 일괄 조회로.
            List<Application> applications = adminApplicationService.listSessionApplications(session.getId());
            applications.forEach(application -> distinctApplications.putIfAbsent(application.getId(), application));

            Map<DiningTableStatus, Long> tables = tableCounts.getOrDefault(session.getId(), Map.of());
            long tableCount = tables.getOrDefault(DiningTableStatus.PROPOSED, 0L)
                    + tables.getOrDefault(DiningTableStatus.CONFIRMED, 0L);
            boolean isMatchRunDone = matchRunSessionIds.contains(session.getId());
            cards.add(DiningDashboardResponse.SessionCard.of(session, applications.stream().filter(this::isPaid).count(),
                    tableCount, isMatchRunDone, openExceptions.getOrDefault(session.getId(), 0L)));
        }

        return DiningDashboardResponse.builder()
                .upcomingSessionCount(sessions.size())
                .waitingApplicantCount(distinctApplications.values().stream().filter(this::isWaitingForMatch).count())
                .paidApplicantCount(distinctApplications.values().stream().filter(this::isPaid).count())
                .proposedTableCount(sumTables(tableCounts, DiningTableStatus.PROPOSED))
                .confirmedTableCount(sumTables(tableCounts, DiningTableStatus.CONFIRMED))
                .openExceptionCount(exceptionCaseRepository.countByStatus(ExceptionCaseStatus.OPEN))
                .sessions(cards)
                .build();
    }

    public List<DiningApplicantResponse> listApplicants(UUID sessionId) {
        gatheringService.findRandomTableSession(sessionId);
        List<Application> applications = adminApplicationService.listSessionApplications(sessionId);
        List<UUID> applicationIds = applications.stream().map(Application::getId).toList();
        Map<UUID, Map<String, Object>> answers = new HashMap<>();
        Map<UUID, Object> birthYears = new HashMap<>();
        for (ApplicationAnswer answer : adminApplicationService.findAnswers(applicationIds)) {
            UUID applicationId = answer.getApplication().getId();
            FormQuestion question = answer.getQuestion();
            Object value = answer.getValue() != null ? answer.getValue().get("value") : null;
            if (question.getReservedKey() == ReservedQuestionKey.BIRTH_YEAR) {
                birthYears.put(applicationId, value);
            } else if (PROFILE_QUESTION_KEYS.contains(question.getQuestionKey())) {
                answers.computeIfAbsent(applicationId, id -> new HashMap<>()).put(question.getQuestionKey(), value);
            }
        }
        Map<UUID, List<ApplicationCandidateSession>> candidates =
                adminApplicationService.findCandidateSessions(applicationIds);
        Map<UUID, Long> participation = diningAttendanceService.countAttendedByUserIds(applications.stream()
                .map(Application::getUser).filter(Objects::nonNull).map(User::getId).distinct().toList());
        Set<UUID> excludedUserIds = findUsersWithExcludedRelation(applications);

        return applications.stream()
                .map(application -> {
                    Map<String, Object> answer = answers.getOrDefault(application.getId(), Map.of());
                    UUID userId = application.getUser() != null ? application.getUser().getId() : null;
                    Integer age = age(birthYears.get(application.getId()), application.getUser());
                    return DiningApplicantResponse.of(application,
                            age != null ? age.toString() : null,
                            text(answer.get(GENDER_QUESTION_KEY)),
                            text(answer.get(MBTI_QUESTION_KEY)),
                            candidates.getOrDefault(application.getId(), List.of()),
                            userId != null ? participation.getOrDefault(userId, 0L) : 0L,
                            userId != null && excludedUserIds.contains(userId));
                })
                .toList();
    }

    // 이 회차의 진행 중(반려·취소 아님) 신청자끼리 제외 관계(피하고 싶음·신고)가 있는 회원. 관계는 한 방향만 있어도 양쪽 모두 표시한다.
    private Set<UUID> findUsersWithExcludedRelation(List<Application> applications) {
        List<UUID> userIds = applications.stream()
                .filter(application -> !CLOSED_APPLICATION_STATUSES.contains(application.getStatus()))
                .map(Application::getUser).filter(Objects::nonNull).map(User::getId).distinct().toList();
        if (userIds.size() < 2) {
            return Set.of();
        }
        Set<UUID> candidates = Set.copyOf(userIds);
        Set<UUID> result = new HashSet<>();
        matchExclusionProvider.findExcludedPairs(userIds).forEach((userId, others) -> others.stream()
                .filter(other -> candidates.contains(userId) && candidates.contains(other) && !other.equals(userId))
                .forEach(other -> {
                    result.add(userId);
                    result.add(other);
                }));
        return result;
    }

    public byte[] exportApplicantsCsv(UUID sessionId) {
        List<List<?>> rows = listApplicants(sessionId).stream()
                .<List<?>>map(applicant -> Arrays.asList(
                        applicant.getApplicationId(),
                        applicant.getName(),
                        applicant.getAge(),
                        applicant.getGender(),
                        applicant.getMbti(),
                        applicant.getStatus(),
                        Boolean.TRUE.equals(applicant.getIsPaid()) ? "완료" : "미완료",
                        applicant.getMatchStatus(),
                        applicant.getCandidateSessions().stream()
                                .map(candidate -> candidate.getPriority() + "순위 " + candidate.getEventDate())
                                .collect(Collectors.joining(" / ")),
                        applicant.getParticipationCount(),
                        Boolean.TRUE.equals(applicant.getHasExcludedRelation()) ? "Y" : "N"))
                .toList();
        return CsvWriter.write(APPLICANT_CSV_HEADER, rows);
    }

    /** 테이블(dining_tables, 해체 제외) 결과 CSV. 멤버 1명당 1행, 멤버가 없는 테이블은 테이블 정보만 1행. */
    public byte[] exportTablesCsv(UUID sessionId) {
        gatheringService.findRandomTableSession(sessionId);
        List<MatchingResultResponse.GroupView> tables = matchingService.getMatchingResult(sessionId).getGroups();
        Map<UUID, String> venueNames = venueRepository.findAllById(tables.stream()
                        .map(MatchingResultResponse.GroupView::getVenueId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));

        List<List<?>> rows = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            MatchingResultResponse.GroupView table = tables.get(i);
            // 식당 풀에서 배정한 식당이 없으면 기존 수기 입력 식당명을 쓴다.
            String venue = table.getVenueId() != null ? venueNames.get(table.getVenueId()) : table.getRestaurantName();
            List<Object> tableCells = Arrays.asList(i + 1, table.getGroupId(), table.getStatus(), table.getGroupScore(),
                    table.getGroupSize(), venue);
            if (table.getMembers().isEmpty()) {
                rows.add(tableCells);
                continue;
            }
            for (MatchingResultResponse.MemberView member : table.getMembers()) {
                List<Object> row = new ArrayList<>(tableCells);
                row.addAll(Arrays.asList(member.getSeatOrder(), member.getApplicationId(), member.getName()));
                rows.add(row);
            }
        }
        return CsvWriter.write(TABLE_CSV_HEADER, rows);
    }

    private boolean isPaid(Application application) {
        return ApplicationStatus.SEAT_OCCUPYING.contains(application.getStatus());
    }

    // Set.of는 contains(null)에서 NPE를 던지므로 matchStatus가 없는 레거시 신청을 먼저 거른다.
    private boolean isWaitingForMatch(Application application) {
        return application.getMatchStatus() != null
                && WAITING_MATCH_STATUSES.contains(application.getMatchStatus())
                && !CLOSED_APPLICATION_STATUSES.contains(application.getStatus());
    }

    private static long sumTables(Map<UUID, Map<DiningTableStatus, Long>> tableCounts, DiningTableStatus status) {
        return tableCounts.values().stream().mapToLong(counts -> counts.getOrDefault(status, 0L)).sum();
    }

    private static String text(Object value) {
        return value != null ? value.toString() : null;
    }

    // 출생연도 답으로 계산한 나이. 답이 없거나 연도로 읽을 수 없으면 회원 나이(User.getCurrentAge, 만 나이).
    // 회원 생년월일의 연도와 같으면 생일까지 반영한 회원 만 나이를 쓴다.
    // ponytail: 그 밖엔 생일을 몰라 올해 생일이 지난 것으로 본다(만 나이보다 최대 1살 많음). 정확히 하려면 생년월일 질문으로.
    private static Integer age(Object birthYearAnswer, User user) {
        Integer memberAge = user != null ? user.getCurrentAge() : null;
        Integer birthYear = parseYear(birthYearAnswer);
        int thisYear = Year.now().getValue();
        if (birthYear == null || birthYear < MIN_BIRTH_YEAR || birthYear > thisYear) {
            return memberAge;
        }
        if (user != null && user.getBirthDate() != null && user.getBirthDate().getYear() == birthYear) {
            return memberAge;
        }
        return thisYear - birthYear;
    }

    private static Integer parseYear(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
