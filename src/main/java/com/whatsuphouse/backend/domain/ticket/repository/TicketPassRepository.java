package com.whatsuphouse.backend.domain.ticket.repository;

import com.whatsuphouse.backend.domain.ticket.entity.TicketPass;
import com.whatsuphouse.backend.domain.ticket.enums.TicketPassStatus;
import com.whatsuphouse.backend.domain.ticket.enums.TicketProductKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.whatsuphouse.backend.global.common.repository.LockRefreshRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketPassRepository extends JpaRepository<TicketPass, UUID>, LockRefreshRepository<TicketPass> {

    // 이용권 소유자(회원) 기준 조회.
    List<TicketPass> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<TicketPass> findByIdAndDeletedAtIsNull(UUID id);

    // 입금 확인 중복 호출(웹훅 재시도·관리자 동시 클릭)을 직렬화한다. 뒤 호출은 ACTIVE를 보고 TICKET_ALREADY_PROCESSED.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketPass t where t.id = :id and t.deletedAt is null")
    Optional<TicketPass> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    List<TicketPass> findByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(TicketPassStatus status);

    long countByStatusAndDeletedAtIsNull(TicketPassStatus status);

    // 우연한 식탁 신청 시 차감 대상(가장 먼저 활성화된 사용 가능 이용권)을 동시성 안전하게 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketPass t " +
            "where t.user.id = :userId and t.status = :status " +
            "and t.remainingCount > 0 and t.deletedAt is null " +
            "order by t.activatedAt asc")
    List<TicketPass> findUsableByUserForUpdate(@Param("userId") UUID userId,
                                               @Param("status") TicketPassStatus status,
                                               Pageable pageable);

    // 회원의 활성 멤버십(만료일 빠른 순). 회차 날짜 커버 여부는 TicketPass.isUsableOn으로 거른다. (KAN-401)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketPass t " +
            "where t.user.id = :userId and t.status = :status " +
            "and t.productOption.kind = :kind and t.deletedAt is null " +
            "order by t.validUntil asc")
    List<TicketPass> findMembershipsByUserForUpdate(@Param("userId") UUID userId,
                                                    @Param("status") TicketPassStatus status,
                                                    @Param("kind") TicketProductKind kind);

    // 만료 대상: 만료일이 기준일 이전인 활성 멤버십.
    List<TicketPass> findByStatusAndProductOption_KindAndValidUntilBeforeAndDeletedAtIsNull(
            TicketPassStatus status, TicketProductKind kind, LocalDate date);

    // 입금 알림·후보 목록의 대상 이름 일괄 조회. (KAN-426)
    List<TicketPass> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
