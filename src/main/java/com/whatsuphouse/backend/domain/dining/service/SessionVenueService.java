package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     * 회차 식당 풀에서 자리(used < capacity)가 남은 활성 식당의 used_tables를 1 올리고 그 ID를 돌려준다. 없으면 empty. 테이블 자동 확정(KAN-346)이 쓴다.
     * preferredPriceRanges(PRICE_RANGE 선택지 문구)가 있으면 식당 가격대가 거기 드는 식당을 먼저, 없으면 venue id 순 첫 식당. (KAN-407)
     * 식당 가격대를 해석할 수 없으면 선호가 없는 것으로 본다.
     */
    @Transactional
    public Optional<UUID> useFirstAvailableVenue(UUID sessionId, Set<String> preferredPriceRanges) {
        List<SessionVenue> pool = sessionVenueRepository.findBySessionIdForUpdate(sessionId);
        Map<UUID, Venue> venues = venueRepository.findAllById(pool.stream().map(SessionVenue::getVenueId).toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity()));
        List<SessionVenue> open = pool.stream()
                .filter(sv -> sv.getUsedTables() < sv.getCapacityTables()
                        && venues.get(sv.getVenueId()) != null && venues.get(sv.getVenueId()).isAssignable())
                .toList();
        Optional<SessionVenue> chosen = open.stream()
                .filter(sv -> PriceRangeOption.ofVenueText(venues.get(sv.getVenueId()).getPriceRange())
                        .map(option -> preferredPriceRanges.contains(option.getLabel())).orElse(false))
                .findFirst()
                .or(() -> open.stream().findFirst());
        chosen.ifPresent(SessionVenue::useTable);
        return chosen.map(SessionVenue::getVenueId);
    }

    /** 표시용 식당 조회. 배정 후 삭제된 식당도 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<Venue> findVenue(UUID venueId) {
        return venueRepository.findById(venueId);
    }
}
