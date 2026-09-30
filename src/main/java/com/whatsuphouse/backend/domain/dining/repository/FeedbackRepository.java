package com.whatsuphouse.backend.domain.dining.repository;

import com.whatsuphouse.backend.domain.dining.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    boolean existsByTableMemberId(UUID tableMemberId);

    List<Feedback> findByTableMemberIdIn(Collection<UUID> tableMemberIds);

    @Query("select f.tableMemberId from Feedback f where f.tableMemberId in :tableMemberIds")
    List<UUID> findTableMemberIdsIn(@Param("tableMemberIds") Collection<UUID> tableMemberIds);
}
