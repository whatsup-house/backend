package com.whatsuphouse.backend.domain.matching.service;

import com.whatsuphouse.backend.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DiningConfirmServiceSystemAdminTest {

    private static final UUID CONFIGURED = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ADMIN = UUID.fromString("b1000000-0000-0000-0000-000000000002");

    @Mock
    private UserService userService;

    @InjectMocks
    private DiningConfirmService diningConfirmService;

    @Test
    @DisplayName("설정된 운영자 계정이 활성 관리자면 그 계정을 쓴다")
    void systemAdminId_configuredActive() {
        ReflectionTestUtils.setField(diningConfirmService, "systemAdminIdSetting", CONFIGURED.toString());
        given(userService.listActiveAdminIds()).willReturn(List.of(OTHER_ADMIN, CONFIGURED));

        assertThat(diningConfirmService.systemAdminId()).isEqualTo(CONFIGURED);
    }

    @Test
    @DisplayName("설정된 운영자 계정이 활성 관리자가 아니면 활성 관리자 첫 명으로 대신한다")
    void systemAdminId_configuredInactive_fallsBack() {
        ReflectionTestUtils.setField(diningConfirmService, "systemAdminIdSetting", CONFIGURED.toString());
        given(userService.listActiveAdminIds()).willReturn(List.of(OTHER_ADMIN));

        assertThat(diningConfirmService.systemAdminId()).isEqualTo(OTHER_ADMIN);
    }
}
