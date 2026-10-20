package com.whatsuphouse.backend.domain.bot.repository;

import com.whatsuphouse.backend.domain.bot.entity.BotDispatch;
import com.whatsuphouse.backend.domain.bot.enums.BotDispatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotDispatchRepository extends JpaRepository<BotDispatch, UUID> {

    boolean existsByMessageId(UUID messageId);

    Optional<BotDispatch> findByEventId(UUID eventId);

    // 답변 수신: 같은 eventId 동시 호출을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from BotDispatch d where d.eventId = :eventId")
    Optional<BotDispatch> findByEventIdForUpdate(@Param("eventId") UUID eventId);

    // 디바운스 대상. 조회(lease)가 이 행을 잠그고 있으면 커밋을 기다렸다가 상태를 다시 본다(LEASED면 새 PENDING).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BotDispatch> findFirstByConversationIdAndStatus(UUID conversationId, BotDispatchStatus status);

    boolean existsByConversationIdAndCreatedAtAfter(UUID conversationId, LocalDateTime createdAt);

    long countByCustomerIdAndCreatedAtGreaterThanEqual(UUID customerId, LocalDateTime from);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    List<BotDispatch> findAllByConversationIdInAndHandoffReasonIsNotNull(Collection<UUID> conversationIds);

    /** 마지막 신호 이후 조회 가능해진 PENDING이 있는지(Slack 신호 판단). */
    boolean existsByStatusAndSendAfterGreaterThanAndSendAfterLessThanEqual(
            BotDispatchStatus status, LocalDateTime after, LocalDateTime upTo);

    /**
     * 조회 가능한(디바운스 끝) PENDING을 오래된 순으로 limit건 잠근다. 다른 호출이 잡은 행은 건너뛴다(동시 조회 중복 방지).
     * 네이티브인 이유: JPQL에는 SKIP LOCKED가 없다. ID를 문자열로 받는 이유: 네이티브 UUID 스칼라는 DB마다 타입이 다르다.
     */
    @Query(value = """
            SELECT CAST(d.id AS VARCHAR(36)) FROM bot_dispatch d
            WHERE d.status = 'PENDING' AND d.send_after <= :now
            ORDER BY d.send_after
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** lease 기한이 지난 LEASED. 대체 여부(같은 방 더 새 dispatch)는 호출 측이 본다. */
    @Query(value = """
            SELECT CAST(d.id AS VARCHAR(36)) FROM bot_dispatch d
            WHERE d.status = 'LEASED' AND d.leased_until <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockExpiredLeaseIds(@Param("now") LocalDateTime now);

    /** 조회 가능해진 뒤(send_after) 기준 시각까지 아무도 가져가지 않은 PENDING. */
    @Query(value = """
            SELECT CAST(d.id AS VARCHAR(36)) FROM bot_dispatch d
            WHERE d.status = 'PENDING' AND d.send_after <= :dueBefore
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockUnclaimedIds(@Param("dueBefore") LocalDateTime dueBefore);
}
