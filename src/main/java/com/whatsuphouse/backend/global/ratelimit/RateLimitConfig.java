package com.whatsuphouse.backend.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(redisTemplate, objectMapper));
        // 시큐리티 필터 체인보다 먼저 실행해 인증 비용 없이 차단한다
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
