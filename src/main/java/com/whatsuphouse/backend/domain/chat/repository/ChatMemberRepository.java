package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, UUID> {

    interface ReadState {
        UUID getUserId();
        LocalDateTime getReadAt();
    }

    Optional<ChatMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<ChatMember> findAllByRoomIdAndLeftAtIsNull(UUID roomId);

    List<ChatMember> findAllByRoomIdAndUserIdIn(UUID roomId, Collection<UUID> userIds);

    List<ChatMember> findAllByUserIdAndLeftAtIsNull(UUID userId);

    @Query("""
            select m.roomId as roomId, count(m) as count from ChatMember m
            where m.roomId in :roomIds and m.leftAt is null
            group by m.roomId
            """)
    List<ChatMessageRepository.RoomCount> countActiveByRoomIds(@Param("roomIds") Collection<UUID> roomIds);

    // 참여 중인 멤버별 마지막으로 읽은 메시지 시각(메시지별 안 읽은 사람 수 계산용)
    @Query("""
            select m.userId as userId, lr.createdAt as readAt from ChatMember m
            left join ChatMessage lr on lr.id = m.lastReadMessageId
            where m.roomId = :roomId and m.leftAt is null
            """)
    List<ReadState> findReadStates(@Param("roomId") UUID roomId);
}
