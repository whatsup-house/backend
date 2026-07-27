package com.whatsuphouse.backend.domain.mileage.repository;

import com.whatsuphouse.backend.domain.mileage.entity.MileageHistory;
import com.whatsuphouse.backend.domain.mileage.enums.MileageType;
import com.whatsuphouse.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MileageHistoryRepository extends JpaRepository<MileageHistory, UUID> {

    boolean existsByUserAndTypeAndRelatedId(User user, MileageType type, UUID relatedId);

    Page<MileageHistory> findByUserId(UUID userId, Pageable pageable);

    Page<MileageHistory> findByUserIdAndType(UUID userId, MileageType type, Pageable pageable);
}
