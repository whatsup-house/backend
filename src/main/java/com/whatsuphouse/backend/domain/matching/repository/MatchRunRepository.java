package com.whatsuphouse.backend.domain.matching.repository;

import com.whatsuphouse.backend.domain.matching.entity.MatchRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRunRepository extends JpaRepository<MatchRun, UUID> {

    Optional<MatchRun> findFirstBySessionIdOrderByStartedAtDesc(UUID sessionId);

    @Query("select distinct r.sessionId from MatchRun r where r.sessionId in :sessionIds")
    List<UUID> findSessionIdsIn(@Param("sessionIds") Collection<UUID> sessionIds);
}
