package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserIdIn(Collection<UUID> userIds);

    void deleteByEndpointAndUserId(String endpoint, UUID userId);
}
