package com.whatsuphouse.backend.domain.form.entity;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "application_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAnswer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private FormQuestion question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> value;

    @Builder
    public ApplicationAnswer(Application application, FormQuestion question, Map<String, Object> value) {
        this.application = application;
        this.question = question;
        this.value = value;
    }
}
