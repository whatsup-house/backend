package com.whatsuphouse.backend.domain.chat.repository;

import com.whatsuphouse.backend.domain.chat.entity.ChatRoom;
import com.whatsuphouse.backend.domain.chat.enums.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByIdAndDeletedAtIsNull(UUID id);

    // 문의방은 삭제 대상이 아니므로(삭제는 GROUP만) deleted 조건 없이 사용자당 1개를 찾는다.
    Optional<ChatRoom> findByInquiryUserId(UUID inquiryUserId);

    List<ChatRoom> findAllByDeletedAtIsNull();

    // 내 방 목록: 참여 중 & 숨기지 않은 방
    @Query("""
            select r from ChatRoom r
            join ChatMember m on m.roomId = r.id
            where m.userId = :userId and m.leftAt is null and m.hidden = false and r.deletedAt is null
            """)
    List<ChatRoom> findVisibleRooms(@Param("userId") UUID userId);

    @Query("""
            select r.id from ChatRoom r
            where r.type = :type and r.deletedAt is null
              and not exists (select 1 from ChatMember m where m.roomId = r.id and m.userId = :userId)
            """)
    List<UUID> findRoomIdsWithoutMember(@Param("type") ChatRoomType type, @Param("userId") UUID userId);
}
