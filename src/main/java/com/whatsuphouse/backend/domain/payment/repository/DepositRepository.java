package com.whatsuphouse.backend.domain.payment.repository;

import com.whatsuphouse.backend.domain.payment.entity.Deposit;
import com.whatsuphouse.backend.domain.payment.enums.DepositStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {

    Optional<Deposit> findByApplication_Id(UUID applicationId);

    List<Deposit> findByApplication_IdIn(Collection<UUID> applicationIds);

    // 입금 대사 후보: 취소되지 않은 신청의 입금 대기 보증금.
    @EntityGraph(attributePaths = {"application", "application.user"})
    List<Deposit> findByStatusAndApplication_DeletedAtIsNullOrderByCreatedAtAsc(DepositStatus status);
}
