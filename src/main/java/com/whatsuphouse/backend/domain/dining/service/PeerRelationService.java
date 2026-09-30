package com.whatsuphouse.backend.domain.dining.service;

import com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind;
import com.whatsuphouse.backend.domain.dining.repository.PeerPreferenceRepository;
import com.whatsuphouse.backend.domain.matching.service.MatchExclusionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 매칭 엔진이 쓰는 회원 쌍 관계(옛 DefaultMatchExclusionProvider 빈 구현을 대체, KAN-350).
 * 채팅의 ChatMute는 운영자가 건 회원 단위 채팅 금지라 회원 쌍 관계가 아니어서 반영하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PeerRelationService implements MatchExclusionProvider {

    private final PeerPreferenceRepository peerPreferenceRepository;

    /** AVOID 선호(단방향이라도)와 신고(신고자·피신고자)를 한 번의 쿼리로 모은다. */
    @Override
    public Map<UUID, Set<UUID>> findExcludedPairs(Collection<UUID> userIds) {
        return userIds.isEmpty() ? Map.of() : toMap(peerPreferenceRepository.findExcludedPairs(userIds));
    }

    @Override
    public Map<UUID, Set<UUID>> findAgainPairs(Collection<UUID> userIds) {
        return userIds.isEmpty() ? Map.of()
                : toMap(peerPreferenceRepository.findPairsByKind(userIds, PeerPreferenceKind.AGAIN));
    }

    private static Map<UUID, Set<UUID>> toMap(List<PeerPreferenceRepository.UserPairProjection> pairs) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        pairs.forEach(p -> result.computeIfAbsent(p.getUserId(), id -> new HashSet<>()).add(p.getOtherUserId()));
        return result;
    }
}
