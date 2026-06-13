package com.whatsuphouse.backend.domain.mailtemplate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자가 운영 중 수정한 알림 메일 제목/본문 오버라이드. (KAN-244)
 * template_key는 MailTemplateType의 이름과 1:1 매핑된다. 행이 없으면 MailTemplateType의 기본값을 사용한다.
 */
@Entity
@Table(name = "mail_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_key", nullable = false, unique = true, length = 50)
    private String templateKey;

    @Column(length = 100)
    private String description;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public MailTemplate(String templateKey, String description, String subject, String body) {
        this.templateKey = templateKey;
        this.description = description;
        this.subject = subject;
        this.body = body;
    }

    public void updateContent(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }
}
