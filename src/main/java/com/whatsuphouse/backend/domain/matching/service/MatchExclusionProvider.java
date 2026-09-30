package com.whatsuphouse.backend.domain.matching.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 같은 테이블에 앉으면 안 되는 회원 쌍(하드 조건 3). 회원 ID → 제외 상대 회원 ID. 한 방향만 담아도 엔진이 양방향으로 본다.
 * 출처: peer_preferences(AVOID)·safety_reports(양방향)·채팅 뮤트 관계. (설계 4.3)
 */
public interface MatchExclusionProvider {

    Map<UUID, Set<UUID>> findExcludedPairs(Collection<UUID> userIds);
}
