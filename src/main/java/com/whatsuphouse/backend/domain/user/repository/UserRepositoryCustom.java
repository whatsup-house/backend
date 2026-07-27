package com.whatsuphouse.backend.domain.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {

    Page<UserApplicationStatsProjection> findUsersWithApplicationStats(String search, Pageable pageable);
}
