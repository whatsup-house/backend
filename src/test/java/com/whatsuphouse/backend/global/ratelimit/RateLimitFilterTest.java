package com.whatsuphouse.backend.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private void stubCount(long count) {
        given(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .willReturn(count);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRequestURI("/api/auth/login");
        return request;
    }

    @Test
    @DisplayName("한도 이내 요청은 통과한다")
    void underLimit_passes() throws Exception {
        stubCount(3L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(loginRequest(), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("로그인 한도(5회/분) 초과 시 429와 ApiResult 실패 응답을 반환한다")
    void overLimit_returns429() throws Exception {
        stubCount(6L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(loginRequest(), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("\"success\":false");
    }

    @Test
    @DisplayName("비회원 신청 경로 패턴(/api/gatherings/*/applications/guest)도 제한 대상이다")
    void guestApplyPattern_matched() throws Exception {
        stubCount(11L);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/gatherings/123e4567-e89b-12d3-a456-426614174000/applications/guest");
        request.setRequestURI("/api/gatherings/123e4567-e89b-12d3-a456-426614174000/applications/guest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("제한 대상이 아닌 경로는 Redis를 거치지 않고 통과한다")
    void nonTargetPath_passesWithoutRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/gatherings");
        request.setRequestURI("/api/gatherings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("X-Forwarded-For가 있으면 마지막(Caddy가 append한 실제) IP를 키에 사용한다")
    @SuppressWarnings("unchecked")
    void usesLastForwardedForIp() throws Exception {
        stubCount(1L);
        MockHttpServletRequest request = loginRequest();
        // 클라이언트 위조값(1.2.3.4) + Caddy가 append한 실제 IP(10.0.0.9)
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verify(redisTemplate).execute(
                any(RedisScript.class), eq(List.of("rate-limit:login:10.0.0.9")), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Redis 장애 시에는 차단하지 않고 통과시킨다 (fail-open)")
    @SuppressWarnings("unchecked")
    void redisFailure_failsOpen() throws Exception {
        given(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .willThrow(new RuntimeException("redis down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(loginRequest(), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
