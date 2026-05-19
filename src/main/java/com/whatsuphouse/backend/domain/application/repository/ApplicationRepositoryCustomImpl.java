package com.whatsuphouse.backend.domain.application.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.QApplication;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ApplicationRepositoryCustomImpl implements ApplicationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Application> findApplications(UUID gatheringId, ApplicationStatus status) {
        QApplication application = QApplication.application;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(application.deletedAt.isNull());

        if (gatheringId != null) {
            builder.and(application.gathering.id.eq(gatheringId));
        }
        if (status != null) {
            builder.and(application.status.eq(status));
        }

        return queryFactory
                .selectFrom(application)
                .join(application.gathering).fetchJoin()
                .leftJoin(application.user).fetchJoin()
                .where(builder)
                .fetch();
    }
}
