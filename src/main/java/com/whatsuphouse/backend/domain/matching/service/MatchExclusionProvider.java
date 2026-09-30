package com.whatsuphouse.backend.domain.matching.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 회원 쌍 관계. 회원 ID → 상대 회원 ID. 한 방향만 담아도 엔진이 양방향으로 본다.
 * 구현은 관계 데이터를 가진 dining 도메인(PeerRelationService)에 있다.
 */
public interface MatchExclusionProvider {

    /** 같은 테이블에 앉으면 안 되는 쌍(하드 조건 3). 출처: peer_preferences(AVOID)·safety_reports. (설계 4.3) */
    Map<UUID, Set<UUID>> findExcludedPairs(Collection<UUID> userIds);

    /** 다시 만나고 싶다고 한 쌍(peer_preferences AGAIN). 이전 만남 페널티를 면제한다. (설계 4.4) */
    Map<UUID, Set<UUID>> findAgainPairs(Collection<UUID> userIds);
}
