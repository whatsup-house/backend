package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import com.whatsuphouse.backend.domain.dining.repository.SessionVenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 다른 도메인(매칭)이 테이블 해체 시 회차 식당 사용 수를 돌려놓는 창구. 매칭 서비스에 의존하지 않는다(순환 방지). */
@Service
@RequiredArgsConstructor
public class SessionVenueService {

    private final SessionVenueRepository sessionVenueRepository;

    @Transactional
    public void releaseTable(UUID sessionId, UUID venueId) {
        sessionVenueRepository.findBySessionIdAndVenueIdForUpdate(sessionId, venueId)
                .ifPresent(SessionVenue::releaseTable);
    }
}
