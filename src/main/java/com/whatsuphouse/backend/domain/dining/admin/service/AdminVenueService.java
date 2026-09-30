package com.whatsuphouse.backend.domain.dining.admin.service;

import com.whatsuphouse.backend.domain.dining.admin.dto.request.SessionVenueRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.request.VenueRequest;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.SessionVenueResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.TableVenueResponse;
import com.whatsuphouse.backend.domain.dining.admin.dto.response.VenueResponse;
import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import com.whatsuphouse.backend.domain.gathering.client.service.GatheringService;
import com.whatsuphouse.backend.domain.matching.entity.DiningTable;
import com.whatsuphouse.backend.domain.matching.service.MatchingService;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 식당 풀 CRUD, 회차별 식당 수용 설정, 테이블 식당 배정. (KAN-348) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminVenueService {

    private final VenueRepository venueRepository;
    private final SessionVenueRepository sessionVenueRepository;
    private final GatheringService gatheringService;
    private final MatchingService matchingService;

    public List<VenueResponse> listVenues() {
        return venueRepository.findAllByDeletedAtIsNullOrderByRegionAscNameAsc().stream()
                .map(VenueResponse::from)
                .toList();
    }

    @Transactional
    public VenueResponse createVenue(VenueRequest request) {
        return VenueResponse.from(venueRepository.save(request.toEntity()));
    }

    @Transactional
    public VenueResponse updateVenue(UUID venueId, VenueRequest request) {
        Venue venue = findVenue(venueId);
        venue.update(request.getName(), request.getAddress(), request.getMapUrl(), request.getPriceRange(),
                request.getRegion(), request.getIsActive());
        return VenueResponse.from(venue);
    }

    // soft delete. 이미 배정된 회차 풀·테이블 참조는 그대로 두고, 새 배정만 막힌다(Venue.isAssignable).
    @Transactional
    public void deleteVenue(UUID venueId) {
        findVenue(venueId).delete();
    }

    /**
     * 회차 식당 풀을 요청 목록으로 통째로 바꾼다.
     * - 새로 넣는 식당은 활성이어야 한다(VENUE_INACTIVE 400). 이미 풀에 있던 식당은 비활성이 됐어도 유지·수정할 수 있다.
     * - 수용 수를 배정된 테이블 수보다 작게 줄이거나, 배정된 테이블이 있는 식당을 빼면 VENUE_CAPACITY_EXCEEDED(409).
     */
    @Transactional
    public List<SessionVenueResponse> updateSessionVenues(UUID sessionId, List<SessionVenueRequest> requests) {
        gatheringService.findRandomTableSession(sessionId);

        Map<UUID, Integer> capacities = new LinkedHashMap<>();
        for (SessionVenueRequest request : requests) {
            if (capacities.put(request.getVenueId(), request.getCapacityTables()) != null) {
                throw new CustomException(ErrorCode.DUPLICATE_SESSION_VENUE);
            }
        }
        // 풀에 남아 있는 삭제된 식당도 찾을 수 있도록 삭제 여부와 무관하게 조회한다.
        Map<UUID, Venue> venues = venueRepository.findAllById(capacities.keySet()).stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        if (venues.size() != capacities.size()) {
            throw new CustomException(ErrorCode.VENUE_NOT_FOUND);
        }

        Map<UUID, SessionVenue> current = sessionVenueRepository.findBySessionIdForUpdate(sessionId).stream()
                .collect(Collectors.toMap(SessionVenue::getVenueId, Function.identity()));
        for (SessionVenue removed : current.values()) {
            if (capacities.containsKey(removed.getVenueId())) {
                continue;
            }
            if (removed.isInUse()) {
                throw new CustomException(ErrorCode.VENUE_CAPACITY_EXCEEDED);
            }
            sessionVenueRepository.delete(removed);
        }

        return capacities.entrySet().stream()
                .map(entry -> {
                    SessionVenue sessionVenue = current.get(entry.getKey());
                    if (sessionVenue == null) {
                        if (!venues.get(entry.getKey()).isAssignable()) {
                            throw new CustomException(ErrorCode.VENUE_INACTIVE);
                        }
                        sessionVenue = sessionVenueRepository.save(
                                new SessionVenue(sessionId, entry.getKey(), entry.getValue()));
                    } else {
                        sessionVenue.changeCapacity(entry.getValue());
                    }
                    return SessionVenueResponse.of(sessionVenue, venues.get(entry.getKey()));
                })
                .toList();
    }

    /**
     * 테이블(dining_tables)에 식당을 배정한다. 식당은 활성이고 테이블 회차의 식당 풀에 있어야 하며,
     * 새 식당의 used_tables를 1 올리고(가득 차면 409) 이전 식당의 used_tables를 1 내린다.
     */
    @Transactional
    public TableVenueResponse assignTableVenue(UUID tableId, UUID venueId) {
        Venue venue = findVenue(venueId);
        if (!venue.isActive()) {
            throw new CustomException(ErrorCode.VENUE_INACTIVE);
        }
        DiningTable table = matchingService.findTable(tableId);
        UUID sessionId = table.getSession().getId();
        UUID previousVenueId = table.getVenueId();
        if (!venueId.equals(previousVenueId)) {
            sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, venueId)
                    .orElseThrow(() -> new CustomException(ErrorCode.VENUE_NOT_IN_SESSION))
                    .useTable();
            if (previousVenueId != null) {
                sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, previousVenueId)
                        .ifPresent(SessionVenue::releaseTable);
            }
            table.assignVenue(venueId);
        }
        return TableVenueResponse.of(table.getId(), venue);
    }

    private Venue findVenue(UUID venueId) {
        return venueRepository.findByIdAndDeletedAtIsNull(venueId)
                .orElseThrow(() -> new CustomException(ErrorCode.VENUE_NOT_FOUND));
    }
}
