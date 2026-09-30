package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.DiningTableMember;
import com.whatsuphouse.backend.domain.matching.enums.DiningTableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiningTableMemberRepository extends JpaRepository<DiningTableMember, UUID> {

    interface UserPairProjection {
        UUID getUserId();
        UUID getOtherUserId();
    }

    int countByTable_Id(UUID tableId);

    boolean existsByApplication_IdAndTable_StatusIn(UUID applicationId, Collection<DiningTableStatus> statuses);

    @Query("""
            select m from DiningTableMember m
            join fetch m.application a
            left join fetch a.user
            where m.table.id = :tableId
            order by m.seatOrder asc
            """)
    List<DiningTableMember> findByTableIdWithApplication(@Param("tableId") UUID tableId);

    @Query("""
            select m from DiningTableMember m
            join fetch m.application a
            left join fetch a.user
            where m.table.id in :tableIds
            order by m.seatOrder asc
            """)
    List<DiningTableMember> findByTableIdsWithApplication(@Param("tableIds") Collection<UUID> tableIds);

    // 신청들이 앉은 상태가 statuses인 테이블(보통 활성 PROPOSED|CONFIRMED)과 그 멤버 행. 우연한 식탁 내 신청 조회용. (KAN-342)
    @Query("""
            select m from DiningTableMember m
            join fetch m.table t
            where m.application.id in :applicationIds
              and t.status in :statuses and t.deletedAt is null
            """)
    List<DiningTableMember> findByApplicationIdsWithTable(@Param("applicationIds") Collection<UUID> applicationIds,
                                                          @Param("statuses") Collection<DiningTableStatus> statuses);

    // 이 신청들 중 상태가 statuses인 테이블(보통 활성 PROPOSED|CONFIRMED)에 앉아 있는 신청 ID
    @Query("""
            select distinct m.application.id from DiningTableMember m
            where m.application.id in :applicationIds
              and m.table.status in :statuses and m.table.deletedAt is null
            """)
    List<UUID> findApplicationIdsByTableStatusIn(@Param("applicationIds") Collection<UUID> applicationIds,
                                                 @Param("statuses") Collection<DiningTableStatus> statuses);

    // 이 회원들끼리 상태가 statuses인 같은 테이블에 앉았던 쌍. (a, b)와 (b, a)가 모두 나온다. excludeTableId(NULL 가능) 테이블은 뺀다.
    @Query("""
            select distinct u1.id as userId, u2.id as otherUserId
            from DiningTableMember m1 join m1.application a1 join a1.user u1,
                 DiningTableMember m2 join m2.application a2 join a2.user u2
            where m2.table = m1.table
              and m1.table.status in :statuses and m1.table.deletedAt is null
              and (:excludeTableId is null or m1.table.id <> :excludeTableId)
              and u1.id in :userIds and u2.id in :userIds and u1.id <> u2.id
            """)
    List<UserPairProjection> findUserPairsByTableStatusIn(@Param("userIds") Collection<UUID> userIds,
                                                          @Param("statuses") Collection<DiningTableStatus> statuses,
                                                          @Param("excludeTableId") UUID excludeTableId);
}
