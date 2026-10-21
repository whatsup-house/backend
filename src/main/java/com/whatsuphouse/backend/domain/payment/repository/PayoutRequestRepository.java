package com.whatsuphouse.backend.domain.payment.repository;

import com.whatsuphouse.backend.domain.payment.entity.PayoutRequest;
import com.whatsuphouse.backend.domain.payment.enums.PayoutStatus;
import com.whatsuphouse.backend.domain.payment.enums.PayoutType;
import com.whatsuphouse.backend.global.common.repository.LockRefreshRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID>, LockRefreshRepository<PayoutRequest> {

    // 송금 완료 처리용. 같은 건의 동시 처리(중복 클릭·두 운영자)를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutRequest p where p.id = :id")
    Optional<PayoutRequest> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "user")
    List<PayoutRequest> findByTypeAndStatusOrderByCreatedAtAsc(PayoutType type, PayoutStatus status);

    List<PayoutRequest> findByTicketPassIdInAndType(Collection<UUID> ticketPassIds, PayoutType type);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PayoutRequest p
            where p.applicationId in :applicationIds or p.ticketPassId in :ticketPassIds or p.depositId in :depositIds
            """)
    void deleteLinkedTo(@Param("applicationIds") Collection<UUID> applicationIds,
                        @Param("ticketPassIds") Collection<UUID> ticketPassIds,
                        @Param("depositIds") Collection<UUID> depositIds);

    Optional<PayoutRequest> findByDepositId(UUID depositId);

    List<PayoutRequest> findByStatusAndAccountMaskedFalseAndSentAtBefore(PayoutStatus status, LocalDateTime threshold);
}
