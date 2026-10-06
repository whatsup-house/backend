package com.whatsuphouse.backend.domain.gathering.client.service;

import com.whatsuphouse.backend.domain.gathering.entity.GatheringSession;
import com.whatsuphouse.backend.domain.gathering.repository.GatheringSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 타 도메인이 회차 행에 접근하는 창구. GatheringSessionRepository에만 의존해 신청 도메인과 순환하지 않는다. (KAN-393)
 */
@Service
@RequiredArgsConstructor
public class GatheringSessionService {

    private final GatheringSessionRepository gatheringSessionRepository;

    /**
     * 정원 검사 전 회차 행을 비관적 락으로 조회한다. 잠금은 호출자 트랜잭션이 끝날 때까지 유지돼야 하므로
     * 트랜잭션 밖에서 부르면 실패하게(MANDATORY) 두고 readOnly도 붙이지 않는다.
     * 호출 경로마다 없는 회차의 예외 코드가 달라(SESSION_NOT_FOUND / GATHERING_NOT_FOUND) Optional로 돌려준다. (KAN-393)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GatheringSession> findSessionForUpdate(UUID id) {
        return gatheringSessionRepository.findByIdAndDeletedAtIsNullForUpdate(id);
    }
}
