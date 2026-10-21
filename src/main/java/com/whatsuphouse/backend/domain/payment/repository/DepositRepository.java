package com.whatsuphouse.backend.domain.payment.repository;

import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.whatsuphouse.backend.global.common.repository.LockRefreshRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID>, LockRefreshRepository<Deposit> {

    Optional<Deposit> findByApplication_Id(UUID applicationId);

    // 입금 확인처럼 상태를 바꾸는 처리용. 같은 보증금의 동시 처리를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deposit d where d.application.id = :applicationId")
    Optional<Deposit> findByApplication_IdForUpdate(@Param("applicationId") UUID applicationId);

    List<Deposit> findByApplication_IdIn(Collection<UUID> applicationIds);

    @Query("select d.id from Deposit d where d.application.id in :applicationIds")
    List<UUID> findIdsByApplicationIdIn(@Param("applicationIds") Collection<UUID> applicationIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from Deposit d where d.id in :ids")
    void deleteByIdIn(@Param("ids") Collection<UUID> ids);

    // 입금 대사 후보: 취소되지 않은 신청의 입금 대기 보증금.
    @EntityGraph(attributePaths = {"application", "application.user"})
    List<Deposit> findByStatusAndApplication_DeletedAtIsNullOrderByCreatedAtAsc(DepositStatus status);

    // 심사용 입금 시뮬레이터: 요청자 본인 신청의 보증금인가.
    boolean existsByApplication_IdAndApplication_User_IdAndStatus(UUID applicationId, UUID userId, DepositStatus status);
}
