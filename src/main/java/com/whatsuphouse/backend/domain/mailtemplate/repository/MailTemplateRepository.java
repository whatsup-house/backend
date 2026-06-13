package com.whatsuphouse.backend.domain.mailtemplate.repository;

import com.whatsuphouse.backend.domain.mailtemplate.entity.MailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, UUID> {

    Optional<MailTemplate> findByTemplateKey(String templateKey);
}
