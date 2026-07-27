package com.whatsuphouse.backend.domain.application.repository;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.enums.ApplicationStatus;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepositoryCustom {

    List<Application> findApplications(UUID gatheringId, ApplicationStatus status);
}
