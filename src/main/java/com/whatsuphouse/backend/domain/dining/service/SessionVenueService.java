package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.entity.Venue;
import com.whatsuphouse.backend.domain.dining.enums.PriceRangeOption;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import com.whatsuphouse.backend.domain.dining.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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
     * region(테이블 지역 라벨)이 있으면 그 지역 식당만 본다. 풀에 그 지역의 배정 가능한 식당이 하나도 없으면 그 지역 활성 식당 전부를
     * 수용 1테이블로 풀에 자동 추가(이미 있는 행은 그대로)한 뒤 고른다. region이 null(지역 매칭 전 테이블)이면 풀 전체. (KAN-431)
     */
    @Transactional
    public Optional<UUID> useFirstAvailableVenue(UUID sessionId, String region, Set<String> preferredPriceRanges) {
        List<SessionVenue> pool = sessionVenueRepository.findBySessionIdForUpdate(sessionId);
        Map<UUID, Venue> venues = new HashMap<>(venueRepository.findAllById(pool.stream().map(SessionVenue::getVenueId).toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Function.identity())));
        List<SessionVenue> candidates = pool.stream()
                .filter(sv -> isCandidate(venues.get(sv.getVenueId()), region))
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty() && region != null) {
            // ponytail: 같은 회차·지역 테이블이 동시에 자동 추가하면 한쪽이 PK 충돌로 실패해 VENUE 예외 건이 된다. 잦아지면 회차 행을 잠근다.
            Set<UUID> pooled = pool.stream().map(SessionVenue::getVenueId).collect(Collectors.toSet());
            for (Venue venue : venueRepository.findAllByRegionAndDeletedAtIsNullOrderByIdAsc(region)) {
                if (venue.isAssignable() && !pooled.contains(venue.getId())) {
                    venues.put(venue.getId(), venue);
                    candidates.add(sessionVenueRepository.save(new SessionVenue(sessionId, venue.getId(), 1)));
                }
            }
        }
        List<SessionVenue> open = candidates.stream()
                .filter(sv -> sv.getUsedTables() < sv.getCapacityTables())
                .toList();
        Optional<SessionVenue> chosen = open.stream()
                .filter(sv -> PriceRangeOption.ofVenueText(venues.get(sv.getVenueId()).getPriceRange())
                        .map(option -> preferredPriceRanges.contains(option.getLabel())).orElse(false))
                .findFirst()
                .or(() -> open.stream().findFirst());
        chosen.ifPresent(SessionVenue::useTable);
        return chosen.map(SessionVenue::getVenueId);
    }

    private static boolean isCandidate(Venue venue, String region) {
        return venue != null && venue.isAssignable() && (region == null || region.equals(venue.getRegion()));
    }

    /** 표시용 식당 조회. 배정 후 삭제된 식당도 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<Venue> findVenue(UUID venueId) {
        return venueRepository.findById(venueId);
    }
}
