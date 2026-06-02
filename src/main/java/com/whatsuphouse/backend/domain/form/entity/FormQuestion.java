package com.whatsuphouse.backend.domain.form.entity;

import com.whatsuphouse.backend.domain.form.enums.MatchingStrategy;
import com.whatsuphouse.backend.domain.form.enums.QuestionType;
import com.whatsuphouse.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "form_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    @Column(name = "question_key", nullable = false, length = 100)
    private String questionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String label;

    @Column(columnDefinition = "TEXT")
    private String placeholder;

    @Column(nullable = false)
    private boolean required = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> validation;

    @Column(name = "is_matching_field", nullable = false)
    private boolean isMatchingField = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "matching_strategy", length = 100)
    private MatchingStrategy matchingStrategy;

    @Column(name = "matching_weight", precision = 3, scale = 2)
    private BigDecimal matchingWeight;

    @Builder
    public FormQuestion(Form form, String questionKey, QuestionType type, String label,
                        String placeholder, boolean required, int displayOrder,
                        Map<String, Object> options, Map<String, Object> validation,
                        boolean isMatchingField,
                        MatchingStrategy matchingStrategy, BigDecimal matchingWeight) {
        this.form = form;
        this.questionKey = questionKey;
        this.type = type;
        this.label = label;
        this.placeholder = placeholder;
        this.required = required;
        this.displayOrder = displayOrder;
        this.options = options;
        this.validation = validation;
        this.isMatchingField = isMatchingField;
        this.matchingStrategy = matchingStrategy;
        this.matchingWeight = matchingWeight;
    }

    public void update(String questionKey, QuestionType type, String label, String placeholder,
                       boolean required, int displayOrder, Map<String, Object> options,
                       Map<String, Object> validation, boolean isMatchingField,
                       MatchingStrategy matchingStrategy, BigDecimal matchingWeight) {
        this.questionKey = questionKey;
        this.type = type;
        this.label = label;
        this.placeholder = placeholder;
        this.required = required;
        this.displayOrder = displayOrder;
        this.options = options;
        this.validation = validation;
        this.isMatchingField = isMatchingField;
        this.matchingStrategy = matchingStrategy;
        this.matchingWeight = matchingWeight;
    }

    public void softDelete() {
        delete();
    }
}
