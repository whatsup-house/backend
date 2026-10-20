package com.whatsuphouse.backend.domain.payment.repository;

import com.whatsuphouse.backend.domain.payment.entity.DepositNotification;
import com.whatsuphouse.backend.domain.payment.enums.DepositMatchResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DepositNotificationRepository extends JpaRepository<DepositNotification, UUID> {

    Optional<DepositNotification> findByTextHash(String textHash);

    Page<DepositNotification> findAllByResult(DepositMatchResult result, Pageable pageable);

    // 수동 연결처럼 결과를 바꾸는 처리용. 같은 알림의 동시 연결을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from DepositNotification n where n.id = :id")
    Optional<DepositNotification> findByIdForUpdate(@Param("id") UUID id);
}
