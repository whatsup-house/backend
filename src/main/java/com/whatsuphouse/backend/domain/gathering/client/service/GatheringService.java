package com.whatsuphouse.backend.domain.gathering.client.service;

import com.whatsuphouse.backend.domain.application.client.service.ApplicationService;
import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringSessionResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringRepository;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import com.whatsuphouse.backend.domain.translation.enums.TranslatableType;
import com.whatsuphouse.backend.domain.translation.service.ContentTranslationService;
import com.whatsuphouse.backend.global.common.enums.AppLocale;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GatheringService {

    private static final Comparator<GatheringSession> SESSION_ORDER = Comparator
            .comparing(GatheringSession::getEventDate)
            .thenComparing(GatheringSession::getStartTime, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final GatheringRepository gatheringRepository;
    private final GatheringSessionRepository gatheringSessionRepository;
    private final ContentTranslationService contentTranslationService;
    private final ApplicationService applicationService;

    /**
     * 종류 단위 목록. 각 종류에는 조건(date, 유효 status)에 맞는 회차만 붙고, 조건이 없으면 오늘 이후 회차(다가오는 회차)만 본다.
     * 맞는 회차가 없는 종류는 빠진다. 순서는 각 종류의 가장 이른 회차 순. (KAN-338)
     * status는 유효 상태로 거른다: 날짜가 지난 OPEN 회차는 DONE이라 OPEN 목록에서 빠진다. (KAN-163)
     */
    public List<GatheringResponse> listGatherings(LocalDate date, GatheringSessionStatus status) {
        List<GatheringSession> sessions = findSessions(date, status).stream()
                .filter(session -> status == null || session.getEffectiveSessionStatus() == status)
                .sorted(SESSION_ORDER)
                .toList();
        Map<UUID, Long> seats = countSeats(sessions);
        return sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getGathering().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(group -> GatheringResponse.of(group.get(0).getGathering(), toSessionResponses(group, seats)))
                .toList();
    }

    private List<GatheringSession> findSessions(LocalDate date, GatheringSessionStatus status) {
        if (date != null) {
            return gatheringSessionRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(date);
        }
        // 유효 상태(지난 OPEN → DONE)는 저장된 상태와 다를 수 있어 전체에서 거른다.
        if (status != null) {
            return gatheringSessionRepository.findByDeletedAtIsNull();
        }
        return gatheringSessionRepository.findByEventDateGreaterThanEqualAndDeletedAtIsNull(LocalDate.now());
    }

    // 큐레이션은 종류 단위. 날짜·장소·가격·상태는 대표 회차 값으로 채운다. 회차가 없는 종류는 노출하지 않는다.
    public List<CuratedGatheringResponse> listCuratedGatherings() {
        List<Gathering> curated = gatheringRepository.findByIsCuratedTrueAndDeletedAtIsNullOrderByCuratedRankAsc();
        Map<UUID, GatheringSession> representatives =
                findRepresentativeSessions(curated.stream().map(Gathering::getId).toList());
        return curated.stream()
                .filter(gathering -> representatives.containsKey(gathering.getId()))
                .map(gathering -> CuratedGatheringResponse.from(gathering, representatives.get(gathering.getId())))
                .toList();
    }

    public GatheringDetailResponse getGathering(UUID id) {
        return getGathering(id, AppLocale.KO);
    }

    /**
     * 종류 상세 + 전체 회차. 옛 회차 ID(= 옛 게더링 ID)로 들어오면 그 회차가 속한 종류로 응답한다. (KAN-338)
     * 요청 로케일로 title/description을 번역 적용한다. ko이거나 번역 없으면 원문. (KAN-266)
     */
    public GatheringDetailResponse getGathering(UUID id, AppLocale locale) {
        Gathering gathering = findGathering(id);
        return toDetail(gathering,
                gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gathering.getId())), locale);
    }

    /** 회차 상세: 종류 정보 + 그 회차 1건(sessions). */
    public GatheringDetailResponse getSession(UUID sessionId, AppLocale locale) {
        GatheringSession session = gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        return toDetail(session.getGathering(), List.of(session), locale);
    }

    private GatheringDetailResponse toDetail(Gathering gathering, List<GatheringSession> sessions, AppLocale locale) {
        List<GatheringSessionResponse> sessionResponses = toSessionResponses(sessions);
        if (locale == AppLocale.KO) {
            return GatheringDetailResponse.of(gathering, gathering.getTitle(), gathering.getDescription(), sessionResponses);
        }
        // 번역은 종류(title/description) 단위로 저장된다.
        ContentTranslationService.Localizer localizer =
                contentTranslationService.localizer(TranslatableType.GATHERING, gathering.getId(), locale);
        return GatheringDetailResponse.of(gathering,
                localizer.get("title", gathering.getTitle()),
                localizer.get("description", gathering.getDescription()),
                sessionResponses);
    }

    /** 회차 응답(정원 차지 인원 포함), 날짜·시작 시간 순. */
    public List<GatheringSessionResponse> toSessionResponses(List<GatheringSession> sessions) {
        return toSessionResponses(sessions, countSeats(sessions));
    }

    private static List<GatheringSessionResponse> toSessionResponses(List<GatheringSession> sessions, Map<UUID, Long> seats) {
        return sessions.stream()
                .sorted(SESSION_ORDER)
                .map(session -> GatheringSessionResponse.from(session, seats.getOrDefault(session.getId(), 0L)))
                .toList();
    }

    private Map<UUID, Long> countSeats(List<GatheringSession> sessions) {
        return applicationService.countSeatsBySessionIds(sessions.stream().map(GatheringSession::getId).toList());
    }

    /**
     * "게더링 ID" 자리로 들어온 값을 회차로 해석한다. 아직 회차 ID를 받는 기존 경로(매칭 등)용 호환 규칙.
     * 회차 ID(마이그레이션된 회차는 옛 게더링 ID와 같다)를 먼저 찾고, 없으면 종류 ID로 보고 대표 회차를 쓴다.
     */
    public GatheringSession findSession(UUID id) {
        return gatheringSessionRepository.findByIdAndDeletedAtIsNull(id)
                .or(() -> pickRepresentative(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(id))))
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
    }

    /** 우연한 식탁 회차. findSession과 달리 종류 ID 폴백 없이 회차 ID만 받는다. (KAN-348) */
    public GatheringSession findRandomTableSession(UUID sessionId) {
        GatheringSession session = gatheringSessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        if (session.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.NOT_RANDOM_TABLE_SESSION);
        }
        return session;
    }

    /** 우연한 식탁 회차를 행 잠금으로 가져온다. 같은 회차의 매칭 실행을 직렬화한다. (KAN-345) */
    public GatheringSession lockRandomTableSession(UUID sessionId) {
        GatheringSession session = gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        if (session.getGathering().getGatheringType() != GatheringType.RANDOM_TABLE) {
            throw new CustomException(ErrorCode.NOT_RANDOM_TABLE_SESSION);
        }
        return session;
    }

    /** 종류의 삭제되지 않은 회차 전부(장소 포함). 매칭 실패 신청의 대체 회차 후보용. (KAN-347) */
    public List<GatheringSession> listSessionsByGathering(UUID gatheringId) {
        return gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(gatheringId)).stream()
                .sorted(SESSION_ORDER)
                .toList();
    }

    /** 매칭 시각이 된 모집 중 우연한 식탁 회차를 잠가 마감(CLOSED)하고 ID를 돌려준다. (KAN-346 매칭 스케줄러) */
    @Transactional
    public List<UUID> closeDueRandomTableSessions(LocalDateTime now) {
        List<UUID> sessionIds = gatheringSessionRepository.findDueRandomTableSessionIdsForUpdate(now, now.toLocalDate())
                .stream().map(UUID::fromString).toList();
        gatheringSessionRepository.findAllById(sessionIds)
                .forEach(session -> session.changeStatus(GatheringSessionStatus.CLOSED));
        return sessionIds;
    }

    /** 오늘 이후(오늘 포함) 취소되지 않은 해당 타입 회차, 날짜·시작 시간 순. (KAN-348 운영 대시보드) */
    public List<GatheringSession> listUpcomingSessions(GatheringType type) {
        return gatheringSessionRepository.findByEventDateGreaterThanEqualAndDeletedAtIsNull(LocalDate.now()).stream()
                .filter(session -> session.getGathering().getGatheringType() == type)
                .filter(session -> session.getStatus() != GatheringSessionStatus.CANCELLED)
                .sorted(SESSION_ORDER)
                .toList();
    }

    /** "게더링 ID" 자리로 들어온 값을 종류로 해석한다. 종류 ID를 먼저 찾고, 없으면 회차 ID로 보고 그 회차의 종류를 쓴다. */
    public Gathering findGathering(UUID id) {
        return gatheringRepository.findByIdAndDeletedAtIsNull(id)
                .or(() -> gatheringSessionRepository.findByIdAndDeletedAtIsNull(id).map(GatheringSession::getGathering))
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
    }

    /** 종류 ID → 대표 회차. 회차가 없는 종류는 결과에 없다. */
    public Map<UUID, GatheringSession> findRepresentativeSessions(Collection<UUID> gatheringIds) {
        if (gatheringIds.isEmpty()) {
            return Map.of();
        }
        return gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(gatheringIds).stream()
                .collect(Collectors.groupingBy(session -> session.getGathering().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> pickRepresentative(entry.getValue()).orElseThrow()));
    }

    // 대표 회차: 오늘 이후 취소되지 않은 가장 이른 회차, 없으면 가장 최근 회차.
    private static Optional<GatheringSession> pickRepresentative(List<GatheringSession> sessions) {
        LocalDate today = LocalDate.now();
        return sessions.stream()
                .filter(s -> !s.getEventDate().isBefore(today) && s.getStatus() != GatheringSessionStatus.CANCELLED)
                .min(Comparator.comparing(GatheringSession::getEventDate))
                .or(() -> sessions.stream().max(Comparator.comparing(GatheringSession::getEventDate)));
    }
}
