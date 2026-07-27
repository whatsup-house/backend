package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FormRepository extends JpaRepository<Form, UUID> {

    // 게더링에 연결된 실제 신청폼 (템플릿은 gathering이 없으므로 자동 제외)
    Optional<Form> findByGathering_IdAndDeletedAtIsNull(UUID gatheringId);
}
