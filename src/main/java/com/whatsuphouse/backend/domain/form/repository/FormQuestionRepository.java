package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FormQuestionRepository extends JpaRepository<FormQuestion, UUID> {
}
