package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.Job;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import com.whatsuphouse.backend.global.common.enums.Gender;
import com.whatsuphouse.backend.global.common.enums.Mbti;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 심사용 체험 계정. "체험 시작"을 누를 때마다 데모 회원을 새로 만들고 곧장 로그인시킨다.
 * 공유 계정이면 한 심사위원의 노쇼가 다음 심사위원 시연을 깨기 때문이다. 호출 횟수 제한은 RateLimitFilter(IP당 분당 3회). (KAN-407)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DemoAccountService {

    private static final int SAMPLE_AGE = 28;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public LoginResponse createDemo() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // 이름이 겹치면 입금 시뮬레이터가 후보 여러 건으로 막힌다. 계정마다 다른 접미사를 그대로 쓴다.
        String name = "체험" + suffix;
        User user = User.builder()
                .email("demo+" + suffix + "@whatsup.house")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .name(name)
                .nickname(name)
                .gender(Gender.FEMALE)
                .age(SAMPLE_AGE)
                .birthDate(LocalDate.now().minusYears(SAMPLE_AGE))
                .mbti(Mbti.ENFP)
                .job(Job.SOFTWARE_DEVELOPER.name())
                .intro("심사용 체험 계정입니다.")
                .build();
        user.markDemo();
        user.approveRandomTable();
        userRepository.save(user);
        return authService.issueLogin(user);
    }
}
