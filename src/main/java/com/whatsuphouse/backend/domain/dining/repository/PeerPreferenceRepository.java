package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.PeerPreference;
import com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PeerPreferenceRepository extends JpaRepository<PeerPreference, UUID> {

    interface UserPairProjection {
        UUID getUserId();
        UUID getOtherUserId();
    }

    // 이 회원들 사이의 kind 선호 쌍 (from → to)
    @Query("""
            select distinct p.fromUserId as userId, p.toUserId as otherUserId
            from PeerPreference p
            where p.kind = :kind and p.fromUserId in :userIds and p.toUserId in :userIds
            """)
    List<UserPairProjection> findPairsByKind(@Param("userIds") Collection<UUID> userIds,
                                             @Param("kind") PeerPreferenceKind kind);

    // 이 회원들 사이의 제외 쌍: AVOID 선호(from → to) + 신고(신고자 → 피신고자). 한 방향만 담는다(엔진이 양방향으로 본다).
    @Query("""
            select p.fromUserId as userId, p.toUserId as otherUserId
            from PeerPreference p
            where p.kind = com.whatsuphouse.backend.domain.dining.enums.PeerPreferenceKind.AVOID
              and p.fromUserId in :userIds and p.toUserId in :userIds
            union
            select r.reporterId as userId, r.reportedUserId as otherUserId
            from SafetyReport r
            where r.reporterId in :userIds and r.reportedUserId in :userIds
            """)
    List<UserPairProjection> findExcludedPairs(@Param("userIds") Collection<UUID> userIds);
}
