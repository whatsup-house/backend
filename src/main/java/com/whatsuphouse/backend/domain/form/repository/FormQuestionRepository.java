package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FormQuestionRepository extends JpaRepository<FormQuestion, UUID> {

    List<FormQuestion> findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(Form form);
}
