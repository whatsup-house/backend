package com.whatsuphouse.backend.domain.notification.repository;

import com.whatsuphouse.backend.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    long countByUser_IdAndReadFalseAndDeletedAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID userId);
}
