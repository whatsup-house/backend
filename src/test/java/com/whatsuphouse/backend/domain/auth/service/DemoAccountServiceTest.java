package com.whatsuphouse.backend.domain.auth.service;

import com.whatsuphouse.backend.domain.auth.dto.response.LoginResponse;
import com.whatsuphouse.backend.domain.user.entity.User;
import com.whatsuphouse.backend.domain.user.enums.RandomTableEligibility;
import com.whatsuphouse.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoAccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthService authService;

    @InjectMocks
    private DemoAccountService demoAccountService;

    @Test
    @DisplayName("체험 계정은 데모·우연한 식탁 승인 상태로 만들고 로그인 토큰을 발급한다")
    void createDemo_setsDemoAndApproved() {
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        LoginResponse login = LoginResponse.builder().accessToken("a").refreshToken("r").build();
        given(authService.issueLogin(any(User.class))).willReturn(login);

        LoginResponse result = demoAccountService.createDemo();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.isDemo()).isTrue();
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.getRandomTableEligibility()).isEqualTo(RandomTableEligibility.APPROVED);
        assertThat(user.getName()).matches("체험[0-9a-f]{8}");
        assertThat(user.getEmail()).matches("demo\\+[0-9a-f]{8}@whatsup\\.house");
        // 이름 접미사 = 이메일 접미사(계정마다 고유)
        assertThat(user.getEmail()).isEqualTo("demo+" + user.getName().substring(2) + "@whatsup.house");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getNickname()).isNotBlank();
        // 신청서 phone이 NOT NULL이라 체험 계정에도 임시 번호가 있어야 한다 (KAN-407)
        assertThat(user.getPhone()).matches("^0100000\\d{4}$");
        assertThat(result).isSameAs(login);
        verify(authService).issueLogin(user);
    }
}
