package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.ApplicationAnswer;
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

    // 프리필(ACC-06): 회원의 표준 질문(reserved_key) 답변, 최신 신청 순. 취소한 신청의 답변도 프로필로 쓴다. (KAN-342)
    @Query("""
            select aa from ApplicationAnswer aa
            join fetch aa.question q
            where aa.application.user.id = :userId
              and q.reservedKey is not null
              and aa.deletedAt is null
            order by aa.createdAt desc
            """)
    List<ApplicationAnswer> findReservedAnswersByUserId(@Param("userId") UUID userId);
}
