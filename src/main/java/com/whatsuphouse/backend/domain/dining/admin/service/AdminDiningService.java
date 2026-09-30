package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.application.admin.service.AdminApplicationService;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.ApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.application.enums.MatchStatus;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningApplicantResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.DiningDashboardResponse;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.ExceptionCaseStatus;
import com.whatsuphouse.backend.domain.dining.repository.ExceptionCaseRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.matching.dto.response.MatchingResultResponse;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.global.common.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
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

    // TODO(KAN-341): 표준 폼 reserved_key(BIRTH_YEAR/GENDER/MBTI) 도입 후 question_key 대신 reserved_key로 읽는다.
    private static final String AGE_QUESTION_KEY = "age";
    private static final String GENDER_QUESTION_KEY = "gender";
    private static final String MBTI_QUESTION_KEY = "mbti";
    private static final Set<String> PROFILE_QUESTION_KEYS = Set.of(AGE_QUESTION_KEY, GENDER_QUESTION_KEY, MBTI_QUESTION_KEY);

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
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final VenueRepository venueRepository;

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
        Map<UUID, Map<String, Object>> answers =
                adminApplicationService.findAnswerValues(applicationIds, PROFILE_QUESTION_KEYS);
        Map<UUID, List<ApplicationCandidateSession>> candidates =
                adminApplicationService.findCandidateSessions(applicationIds);
        Map<UUID, Long> participation = adminApplicationService.countRandomTableAttendance(applications.stream()
                .map(Application::getUser).filter(Objects::nonNull).map(User::getId).distinct().toList());

        return applications.stream()
                .map(application -> {
                    Map<String, Object> answer = answers.getOrDefault(application.getId(), Map.of());
                    UUID userId = application.getUser() != null ? application.getUser().getId() : null;
                    return DiningApplicantResponse.of(application,
                            text(answer.get(AGE_QUESTION_KEY)),
                            text(answer.get(GENDER_QUESTION_KEY)),
                            text(answer.get(MBTI_QUESTION_KEY)),
                            candidates.getOrDefault(application.getId(), List.of()),
                            userId != null ? participation.getOrDefault(userId, 0L) : 0L);
                })
                .toList();
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
}
