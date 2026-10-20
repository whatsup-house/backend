package com.whatsuphouse.backend.domain.application.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.entity.QApplication;
import com.whatsuphouse.backend.domain.application.entity.QApplicationCandidateSession;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;
import com.whatsuphouse.backend.domain.gathering.entity.QGatheringSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ApplicationRepositoryCustomImpl implements ApplicationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Application> findApplications(UUID sessionId, ApplicationStatus status) {
        QApplication application = QApplication.application;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(application.deletedAt.isNull());

        if (sessionId != null) {
            // 이 회차에 배정됐거나, 배정 전(우연한 식탁 매칭 전)이고 이 회차를 희망 회차로 고른 신청. (KAN-338)
            // 회차 ID가 아니면 모임 종류 ID로 보고 그 종류의 신청 전체. (KAN-416)
            // V5 이전 종류는 종류 ID = 대표 회차 ID라, 회차로 있는 ID는 항상 회차로 해석한다.
            QApplicationCandidateSession candidate = QApplicationCandidateSession.applicationCandidateSession;
            QGatheringSession sessionRow = new QGatheringSession("sessionRow");
            builder.and(application.session.id.eq(sessionId)
                    .or(application.session.isNull().and(JPAExpressions.selectOne().from(candidate)
                            .where(candidate.application.eq(application), candidate.session.id.eq(sessionId))
                            .exists()))
                    .or(application.gathering.id.eq(sessionId).and(JPAExpressions.selectOne().from(sessionRow)
                            .where(sessionRow.id.eq(sessionId))
                            .notExists())));
        }
        if (status != null) {
            builder.and(application.status.eq(status));
        }

        return queryFactory
                .selectFrom(application)
                .join(application.gathering).fetchJoin()
                .leftJoin(application.session).fetchJoin()
                .leftJoin(application.user).fetchJoin()
                .where(builder)
                // 정렬을 고정해 상태/입금 토글 후 재조회 시 행 순서가 흔들리지 않게 한다. 최신 신청 우선. (KAN-242)
                .orderBy(application.createdAt.desc())
                .fetch();
    }
}
