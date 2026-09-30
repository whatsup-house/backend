package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 테이블 멤버 조회. 모든 조회는 취소로 빠진 멤버(removed_at IS NOT NULL)를 뺀 활성 멤버만 다룬다.
 * 제거된 행은 참석 기록(attendances)과 이력용으로만 남는다. (KAN-347)
 */
public interface DiningTableMemberRepository extends JpaRepository<DiningTableMember, UUID> {

    interface UserPairProjection {
        UUID getUserId();
        UUID getOtherUserId();
    }

    Optional<DiningTableMember> findByIdAndRemovedAtIsNull(UUID id);

    int countByTable_IdAndRemovedAtIsNull(UUID tableId);

    // 신청당 활성 테이블 1개 검사: 제거되지 않은 멤버 행 기준
    boolean existsByApplication_IdAndRemovedAtIsNullAndTable_StatusIn(UUID applicationId,
                                                                      Collection<DiningTableStatus> statuses);

    @Query("""
            select m from DiningTableMember m
            join fetch m.application a
            left join fetch a.user
            where m.table.id = :tableId and m.removedAt is null
            order by m.seatOrder asc
            """)
    List<DiningTableMember> findByTableIdWithApplication(@Param("tableId") UUID tableId);

    @Query("""
            select m from DiningTableMember m
            join fetch m.application a
            left join fetch a.user
            where m.table.id in :tableIds and m.removedAt is null
            order by m.seatOrder asc
            """)
    List<DiningTableMember> findByTableIdsWithApplication(@Param("tableIds") Collection<UUID> tableIds);

    // 위와 같되 취소로 빠진(removed_at) 행도 포함한다. 운영자 참석 탭이 CANCELED_EARLY 기록을 보여 주는 데만 쓴다. (KAN-349)
    @Query("""
            select m from DiningTableMember m
            join fetch m.application a
            left join fetch a.user
            where m.table.id in :tableIds
            order by m.seatOrder asc
            """)
    List<DiningTableMember> findByTableIdsWithApplicationIncludingRemoved(@Param("tableIds") Collection<UUID> tableIds);

    // 신청들이 앉은 상태가 statuses인 테이블(보통 활성 PROPOSED|CONFIRMED)과 그 멤버 행. 우연한 식탁 내 신청 조회용. (KAN-342)
    @Query("""
            select m from DiningTableMember m
            join fetch m.table t
            where m.application.id in :applicationIds and m.removedAt is null
              and t.status in :statuses and t.deletedAt is null
            """)
    List<DiningTableMember> findByApplicationIdsWithTable(@Param("applicationIds") Collection<UUID> applicationIds,
                                                          @Param("statuses") Collection<DiningTableStatus> statuses);

    // 이 신청들 중 상태가 statuses인 테이블(보통 활성 PROPOSED|CONFIRMED)에 앉아 있는 신청 ID
    @Query("""
            select distinct m.application.id from DiningTableMember m
            where m.application.id in :applicationIds and m.removedAt is null
              and m.table.status in :statuses and m.table.deletedAt is null
            """)
    List<UUID> findApplicationIdsByTableStatusIn(@Param("applicationIds") Collection<UUID> applicationIds,
                                                 @Param("statuses") Collection<DiningTableStatus> statuses);

    // 회원의 멤버십(취소한 신청 제외) 중 테이블 상태가 statuses인 것, 최신 회차 순. 참가 이력용. (KAN-350)
    @Query("""
            select m from DiningTableMember m
            join fetch m.table t
            join fetch t.session s
            left join fetch s.location
            join m.application a
            where a.user.id = :userId and a.deletedAt is null
              and t.status in :statuses and t.deletedAt is null
            order by s.eventDate desc, t.createdAt desc
            """)
    List<DiningTableMember> findByUserIdWithTable(@Param("userId") UUID userId,
                                                  @Param("statuses") Collection<DiningTableStatus> statuses);

    // 회차에서 상태가 statuses인 테이블의 멤버(취소한 신청 제외). 피드백 요약용. (KAN-350)
    @Query("""
            select m from DiningTableMember m
            join fetch m.table t
            join m.application a
            where t.session.id = :sessionId and t.status in :statuses and t.deletedAt is null
              and a.deletedAt is null
            order by t.createdAt asc, m.seatOrder asc
            """)
    List<DiningTableMember> findBySessionIdAndTableStatusIn(@Param("sessionId") UUID sessionId,
                                                            @Param("statuses") Collection<DiningTableStatus> statuses);

    // 이 회원들끼리 상태가 statuses인 같은 테이블에 앉았던 쌍. (a, b)와 (b, a)가 모두 나온다. excludeTableId(NULL 가능) 테이블은 뺀다.
    // 취소로 빠진 멤버는 함께 앉지 않았으므로 뺀다.
    @Query("""
            select distinct u1.id as userId, u2.id as otherUserId
            from DiningTableMember m1 join m1.application a1 join a1.user u1,
                 DiningTableMember m2 join m2.application a2 join a2.user u2
            where m2.table = m1.table
              and m1.removedAt is null and m2.removedAt is null
              and m1.table.status in :statuses and m1.table.deletedAt is null
              and (:excludeTableId is null or m1.table.id <> :excludeTableId)
              and u1.id in :userIds and u2.id in :userIds and u1.id <> u2.id
            """)
    List<UserPairProjection> findUserPairsByTableStatusIn(@Param("userIds") Collection<UUID> userIds,
                                                          @Param("statuses") Collection<DiningTableStatus> statuses,
                                                          @Param("excludeTableId") UUID excludeTableId);
}
