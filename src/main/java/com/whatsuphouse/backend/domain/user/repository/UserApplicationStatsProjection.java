package com.whatsuphouse.backend.domain.user.repository;

import com.whatsuphouse.backend.domain.user.entity.User;

public record UserApplicationStatsProjection(User user, long totalApplications, long attendedCount) {
}
