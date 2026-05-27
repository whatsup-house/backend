package com.whatsuphouse.backend.global.config;

import com.whatsuphouse.backend.global.auth.JwtAuthFilter;
import com.whatsuphouse.backend.global.auth.JwtTokenProvider;
import com.whatsuphouse.backend.global.auth.CustomAuthEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestControllerConfig.FakeController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAuthEntryPoint.class, SecurityConfigTest.TestControllerConfig.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ── 공개 API ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("인증 없이 /api/auth/** 에 접근할 수 있다")
    void authEndpoint_noAuth_permitAll() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 없이 GET /api/gatherings/** 에 접근할 수 있다")
    void gatheringsGet_noAuth_permitAll() throws Exception {
        mockMvc.perform(get("/api/gatherings"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 없이 GET /api/users/check-email 에 접근할 수 있다")
    void checkEmail_noAuth_permitAll() throws Exception {
        mockMvc.perform(get("/api/users/check-email"))
                .andExpect(status().isOk());
    }

    // ── 인증 필요 API ────────────────────────────────────────────────────────

    @Test
    @DisplayName("인증 없이 /api/** 에 접근하면 401을 반환한다")
    void protectedApi_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/gatherings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 /api/** 에 접근하면 401을 반환한다")
    void protectedApi_invalidToken_returns401() throws Exception {
        willThrow(new CustomException(ErrorCode.INVALID_TOKEN))
                .given(jwtTokenProvider).validateToken(anyString());

        mockMvc.perform(post("/api/gatherings")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    // ── 관리자 API ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("일반 사용자가 /api/admin/** 에 접근하면 403을 반환한다")
    @WithMockUser(roles = "USER")
    void adminApi_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자가 /api/admin/** 에 접근하면 인가를 통과한다")
    @WithMockUser(roles = "ADMIN")
    void adminApi_adminRole_passesSecurity() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestControllerConfig {

        @RestController
        static class FakeController {
            @PostMapping("/api/auth/login") String authLogin() { return "ok"; }
            @GetMapping("/api/gatherings") String gatherings() { return "ok"; }
            @GetMapping("/api/users/check-email") String checkEmail() { return "ok"; }
            @PostMapping("/api/gatherings") String createGathering() { return "ok"; }
            @GetMapping("/api/admin/users") String adminUsers() { return "ok"; }
        }
    }
}
