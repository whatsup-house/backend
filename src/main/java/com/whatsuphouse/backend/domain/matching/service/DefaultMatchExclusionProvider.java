package com.whatsuphouse.backend.domain.matching.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 지금은 제외 관계 출처가 없다. 채팅의 ChatMute는 운영자가 건 회원 단위 채팅 금지라 회원 쌍 관계가 아니어서 반영하지 않는다.
 * TODO(KAN-350): peer_preferences(AVOID)·safety_reports가 생기면 여기서 조회해 합친다.
 */
@Component
public class DefaultMatchExclusionProvider implements MatchExclusionProvider {

    @Override
    public Map<UUID, Set<UUID>> findExcludedPairs(Collection<UUID> userIds) {
        return Map.of();
    }
}
