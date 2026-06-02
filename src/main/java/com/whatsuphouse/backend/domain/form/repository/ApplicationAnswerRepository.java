package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, UUID> {

    @Query("""
            select aa from ApplicationAnswer aa
            join fetch aa.question q
            where aa.application.id = :applicationId
              and aa.deletedAt is null
            order by q.displayOrder asc
            """)
    List<ApplicationAnswer> findDetailByApplicationId(@Param("applicationId") UUID applicationId);

    // 자동매칭: 여러 신청의 답변을 질문과 함께 일괄 로드
    @Query("""
            select aa from ApplicationAnswer aa
            join fetch aa.question q
            where aa.application.id in :applicationIds
              and aa.deletedAt is null
            """)
    List<ApplicationAnswer> findByApplicationIds(@Param("applicationIds") List<UUID> applicationIds);
}
