package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatMessage;
import com.whatsuphouse.backend.domain.chat.enums.ChatMessageType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    interface RoomCount {
        UUID getRoomId();
        Long getCount();
    }

    Optional<ChatMessage> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ChatMessage> findByIdAndRoomId(UUID id, UUID roomId);

    Optional<ChatMessage> findFirstByRoomIdOrderByCreatedAtDescIdDesc(UUID roomId);

    // 메시지 조회는 joined_at 필터 없이 방 전체(삭제된 메시지도 "삭제된 메시지입니다"로 노출)
    List<ChatMessage> findAllByRoomIdOrderByCreatedAtDescIdDesc(UUID roomId, Pageable pageable);

    @Query("""
            select m from ChatMessage m
            where m.roomId = :roomId
              and (m.createdAt < :at or (m.createdAt = :at and m.id < :id))
            order by m.createdAt desc, m.id desc
            """)
    List<ChatMessage> findBefore(@Param("roomId") UUID roomId, @Param("at") LocalDateTime at,
                                 @Param("id") UUID id, Pageable pageable);

    @Query("""
            select m from ChatMessage m
            where m.roomId = :roomId
              and (m.createdAt > :at or (m.createdAt = :at and m.id > :id))
            order by m.createdAt asc, m.id asc
            """)
    List<ChatMessage> findAfter(@Param("roomId") UUID roomId, @Param("at") LocalDateTime at,
                                @Param("id") UUID id, Pageable pageable);

    @Query("""
            select m from ChatMessage m
            where m.roomId in :roomIds
              and m.createdAt = (select max(x.createdAt) from ChatMessage x where x.roomId = m.roomId)
            """)
    List<ChatMessage> findLastMessages(@Param("roomIds") Collection<UUID> roomIds);

    // 안읽은 수 = last_read_message_id 이후의 비SYSTEM 메시지 수(내 메시지·삭제 메시지 제외)
    @Query("""
            select m.roomId as roomId, count(m) as count
            from ChatMessage m
            join ChatMember cm on cm.roomId = m.roomId and cm.userId = :userId
            left join ChatMessage lr on lr.id = cm.lastReadMessageId
            where m.roomId in :roomIds
              and m.type <> :systemType
              and m.deletedAt is null
              and m.senderId <> :userId
              and (lr.id is null or m.createdAt > lr.createdAt)
            group by m.roomId
            """)
    List<RoomCount> countUnread(@Param("userId") UUID userId, @Param("roomIds") Collection<UUID> roomIds,
                                @Param("systemType") ChatMessageType systemType);
}
