package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.form.entity.ApplicationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, UUID> {

    List<ApplicationAnswer> findByApplicationOrderByQuestion_DisplayOrderAsc(Application application);
}
