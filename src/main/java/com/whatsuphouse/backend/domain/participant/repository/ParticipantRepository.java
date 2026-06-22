package com.whatsuphouse.backend.domain.participant.repository;

import com.whatsuphouse.backend.domain.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByUser_IdAndDeletedAtIsNull(UUID userId);
}
