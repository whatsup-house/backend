package com.whatsuphouse.backend.domain.user.service;

import com.whatsuphouse.backend.domain.user.dto.request.PasswordChangeRequest;
import com.whatsuphouse.backend.domain.user.dto.request.ProfileUpdateRequest;
import com.whatsuphouse.backend.domain.user.dto.request.UserWithdrawRequest;
import com.whatsuphouse.backend.domain.user.dto.response.ProfileResponse;
import com.whatsuphouse.backend.domain.user.dto.response.UserWithdrawResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final CharacterAssetResolver characterAssetResolver;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        User user = findActiveUser(userId);
        return ProfileResponse.from(user, characterAssetResolver.resolve(user.getJob()));
    }

    public ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = findActiveUser(userId);

        if (request.getNickname() != null
                && !request.getNickname().equals(user.getNickname())
                && userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (!Job.isAcceptable(request.getJob())) {
            throw new CustomException(ErrorCode.INVALID_JOB);
        }

        user.updateProfile(request.getNickname(), request.getPhone(), request.getName(), request.getGender(), request.getAge(),
                request.getInstagramId(), request.getMbti(), request.getJob(), request.getIntro());
        return ProfileResponse.from(user, characterAssetResolver.resolve(user.getJob()));
    }

    // 현재 비밀번호 검증 후 새 비밀번호로 변경한다. (KAN-223)
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    public UserWithdrawResponse withdraw(UUID userId, UserWithdrawRequest request) {
        User user = findActiveUser(userId);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        user.withdraw();
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);

        return UserWithdrawResponse.builder()
                .withdrawn(true)
                .build();
    }

    /** 표시용 일괄 조회(채팅 등). 탈퇴 회원도 포함하므로 표시명 처리는 호출 측이 한다. */
    @Transactional(readOnly = true)
    public Map<UUID, User> findUsersByIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<UUID> listActiveAdminIds() {
        return userRepository.findActiveAdminIds();
    }

    /** 우연한 식탁 참여 제한. 예외함 SAFETY 건을 RESTRICT/BAN 조치로 처리할 때 쓴다. (KAN-348) */
    public void restrictRandomTable(UUID userId) {
        findActiveUser(userId).restrictRandomTable();
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
