package com.whatsuphouse.backend.domain.gathering.client.service;

import com.whatsuphouse.backend.domain.gathering.client.dto.response.CuratedGatheringResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringDetailResponse;
import com.whatsuphouse.backend.domain.gathering.common.dto.response.GatheringResponse;
import com.whatsuphouse.backend.domain.gathering.entity.Gathering;
import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringSessionStatus;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringStatus;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GatheringService {

    private final GatheringRepository gatheringRepository;
    private final GatheringSessionRepository gatheringSessionRepository;
    private final ContentTranslationService contentTranslationService;

    // 기존 API 호환(KAN-338 전까지): 목록 항목 하나 = 회차 하나. id는 회차 ID(마이그레이션된 회차는 옛 게더링 ID).
    public List<GatheringResponse> listGatherings(LocalDate date, GatheringStatus status) {
        return findSessions(date, status != null ? GatheringSessionStatus.from(status) : null).stream()
                .filter(session -> isVisibleInList(session, status))
                .map(GatheringResponse::from)
                .toList();
    }

    private List<GatheringSession> findSessions(LocalDate date, GatheringSessionStatus status) {
        if (date != null && status != null) {
            return gatheringSessionRepository.findByEventDateAndStatusAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(
                    date, status);
        }
        if (date != null) {
            return gatheringSessionRepository.findByEventDateAndDeletedAtIsNullOrderByStartTimeAscCreatedAtAsc(date);
        }
        if (status != null) {
            return gatheringSessionRepository.findByStatusAndDeletedAtIsNull(status);
        }
        return gatheringSessionRepository.findByDeletedAtIsNull();
    }

    // status=OPEN(모집중) 조회 시 eventDate가 지난 회차는 모집 목록에서 제외한다. (KAN-163)
    private boolean isVisibleInList(GatheringSession session, GatheringStatus status) {
        if (status == GatheringStatus.OPEN) {
            return !session.getEventDate().isBefore(LocalDate.now());
        }
        return true;
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

    // 요청 로케일로 title/description을 번역 적용해 반환한다. ko이거나 번역 없으면 원문. (KAN-266)
    public GatheringDetailResponse getGathering(UUID id, AppLocale locale) {
        GatheringSession session = findSession(id);
        Gathering gathering = session.getGathering();

        if (locale == AppLocale.KO) {
            return GatheringDetailResponse.from(session);
        }

        // 번역은 종류(title/description) 단위로 저장된다.
        ContentTranslationService.Localizer localizer =
                contentTranslationService.localizer(TranslatableType.GATHERING, gathering.getId(), locale);
        return GatheringDetailResponse.from(
                session,
                localizer.get("title", gathering.getTitle()),
                localizer.get("description", gathering.getDescription())
        );
    }

    /**
     * 기존 API의 "게더링 ID" 자리로 들어온 값을 회차로 해석한다. (KAN-338 전까지의 호환 규칙)
     * 회차 ID(마이그레이션된 회차는 옛 게더링 ID와 같다)를 먼저 찾고, 없으면 종류 ID로 보고 대표 회차를 쓴다.
     */
    public GatheringSession findSession(UUID id) {
        return gatheringSessionRepository.findByIdAndDeletedAtIsNull(id)
                .or(() -> pickRepresentative(gatheringSessionRepository.findByGathering_IdInAndDeletedAtIsNull(List.of(id))))
                .orElseThrow(() -> new CustomException(ErrorCode.GATHERING_NOT_FOUND));
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
