package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, UUID> {
}
