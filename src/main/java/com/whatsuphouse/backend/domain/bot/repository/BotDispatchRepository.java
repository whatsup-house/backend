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

    // 디바운스 대상. 스케줄러가 발송 중(행 잠금)이면 커밋을 기다렸다가 상태를 다시 본다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BotDispatch> findFirstByConversationIdAndStatus(UUID conversationId, BotDispatchStatus status);

    boolean existsByConversationIdAndCreatedAtAfter(UUID conversationId, LocalDateTime createdAt);

    long countByCustomerIdAndCreatedAtGreaterThanEqual(UUID customerId, LocalDateTime from);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    List<BotDispatch> findAllByConversationIdInAndHandoffReasonIsNotNull(Collection<UUID> conversationIds);

    /**
     * 발송할 때가 된 PENDING(디바운스 끝 + 재시도 대기 끝) 1건을 잠근다. 다른 인스턴스가 잡은 행은 건너뛴다.
     * 네이티브인 이유: JPQL에는 SKIP LOCKED가 없다. ID를 문자열로 받는 이유: 네이티브 UUID 스칼라는 DB마다 타입이 다르다.
     */
    @Query(value = """
            SELECT CAST(d.id AS VARCHAR(36)) FROM bot_dispatch d
            WHERE d.status = 'PENDING' AND d.send_after <= :now
              AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= :now)
            ORDER BY d.send_after
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockNextDueId(@Param("now") LocalDateTime now);

    /** 답 없이 sent_at이 기준 시각 이전인 SENT. 같은 방에 더 새 dispatch가 있으면(대체됨) 제외. */
    @Query(value = """
            SELECT CAST(d.id AS VARCHAR(36)) FROM bot_dispatch d
            WHERE d.status = 'SENT' AND d.sent_at <= :sentBefore
              AND NOT EXISTS (SELECT 1 FROM bot_dispatch n
                              WHERE n.conversation_id = d.conversation_id AND n.created_at > d.created_at)
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockTimedOutIds(@Param("sentBefore") LocalDateTime sentBefore);
}
