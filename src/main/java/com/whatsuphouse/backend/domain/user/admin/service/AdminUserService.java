package com.whatsuphouse.backend.domain.user.admin.service;

import com.whatsuphouse.backend.domain.application.entity.Application;
import com.whatsuphouse.backend.domain.application.repository.ApplicationRepository;
import com.whatsuphouse.backend.domain.user.admin.dto.request.UserStatusRequest;
import com.whatsuphouse.backend.domain.user.admin.dto.response.UserDetailResponse;
import com.whatsuphouse.backend.domain.user.admin.dto.response.UserListResponse;
import com.whatsuphouse.backend.domain.user.admin.dto.response.UserPageResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.repository.UserApplicationStatsProjection;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    public UserPageResponse listUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserApplicationStatsProjection> rawPage = userRepository.findUsersWithApplicationStats(search, pageable);

        Page<UserListResponse> dtoPage = rawPage.map(row ->
                UserListResponse.of(row.user(), row.totalApplications(), row.attendedCount())
        );

        return UserPageResponse.from(dtoPage);
    }

    // 회원 상세 조회 (프로필 + 마일리지 + 신청 이력 + 계정 상태). (KAN-188)
    public UserDetailResponse getUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        List<Application> applications = applicationRepository.findByParticipant_User_IdAndDeletedAtIsNull(userId);
        return UserDetailResponse.from(user, applications);
    }

    // 회원 계정 상태 변경(정지/해제). (KAN-188)
    @Transactional
    public void changeStatus(UUID userId, UserStatusRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.changeAccountStatus(request.getStatus());
    }
}
