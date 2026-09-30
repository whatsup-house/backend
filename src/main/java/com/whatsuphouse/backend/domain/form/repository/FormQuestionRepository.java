package com.whatsuphouse.backend.domain.form.repository;

import com.whatsuphouse.backend.domain.form.entity.FormQuestion;
import com.whatsuphouse.backend.domain.form.entity.Form;
import com.whatsuphouse.backend.domain.gathering.enums.GatheringType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FormQuestionRepository extends JpaRepository<FormQuestion, UUID> {

    List<FormQuestion> findByFormAndDeletedAtIsNullOrderByDisplayOrderAsc(Form form);

    // 타입별 템플릿 폼(V6 시드 '우연한 식탁 표준 폼')의 표준 질문
    @Query("""
            select q from FormQuestion q join q.form f
            where f.isTemplate = true and f.gatheringType = :gatheringType and f.deletedAt is null
              and q.reservedKey is not null and q.deletedAt is null
            order by q.displayOrder
            """)
    List<FormQuestion> findTemplateReservedQuestions(@Param("gatheringType") GatheringType gatheringType);
}
