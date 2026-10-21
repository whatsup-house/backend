package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.SessionVenue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// used_tables 증감과 수용 수 변경이 겹쳐 capacity를 넘지 않도록 수정 경로는 행을 잠근 뒤 읽는다.
public interface SessionVenueRepository extends JpaRepository<SessionVenue, SessionVenue.Key> {

    // 회차 테이블을 모두 지운 뒤 식당 사용 수를 0으로 되돌린다. (KAN-442)
    @Modifying(flushAutomatically = true)
    @Query("update SessionVenue sv set sv.usedTables = 0 where sv.sessionId = :sessionId")
    void resetUsedTables(@Param("sessionId") UUID sessionId);

    // 조회 전용(잠금 없음)
    List<SessionVenue> findBySessionIdOrderByVenueIdAsc(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sv from SessionVenue sv where sv.sessionId = :sessionId order by sv.venueId")
    List<SessionVenue> findBySessionIdForUpdate(@Param("sessionId") UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sv from SessionVenue sv where sv.sessionId = :sessionId and sv.venueId = :venueId")
    Optional<SessionVenue> findBySessionIdAndVenueIdForUpdate(@Param("sessionId") UUID sessionId,
                                                             @Param("venueId") UUID venueId);
}
