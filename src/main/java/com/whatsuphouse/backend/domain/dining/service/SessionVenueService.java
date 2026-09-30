package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 다른 도메인(매칭)이 테이블 해체·확정 시 회차 식당 사용 수를 바꾸는 창구. 매칭 서비스에 의존하지 않는다(순환 방지). */
@Service
@RequiredArgsConstructor
public class SessionVenueService {

    private final SessionVenueRepository sessionVenueRepository;
    private final VenueRepository venueRepository;

    @Transactional
    public void releaseTable(UUID sessionId, UUID venueId) {
        sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, venueId)
                .ifPresent(SessionVenue::releaseTable);
    }

    /**
     * 회차 식당 풀에서 자리(used < capacity)가 남은 첫 활성 식당(venue id 순)의 used_tables를 1 올리고 그 ID를 돌려준다.
     * 없으면 empty. 테이블 자동 확정(KAN-346)이 쓴다.
     */
    @Transactional
    public Optional<UUID> useFirstAvailableVenue(UUID sessionId) {
        List<SessionVenue> pool = sessionVenueRepository.findBySessionIdForUpdate(sessionId);
        Map<UUID, Venue> venues = venueRepository.findAllById(pool.stream().map(SessionVenue::getVenueId).toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        for (SessionVenue sessionVenue : pool) {
            Venue venue = venues.get(sessionVenue.getVenueId());
            if (sessionVenue.getUsedTables() < sessionVenue.getCapacityTables() && venue != null && venue.isAssignable()) {
                sessionVenue.useTable();
                return Optional.of(venue.getId());
            }
        }
        return Optional.empty();
    }

    /** 표시용 식당 조회. 배정 후 삭제된 식당도 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<Venue> findVenue(UUID venueId) {
        return venueRepository.findById(venueId);
    }
}
