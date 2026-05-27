package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.GatheringForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GatheringFormRepository extends JpaRepository<GatheringForm, UUID> {
}
