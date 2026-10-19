package com.whatsuphouse.backend.global.config;

import com.whatsuphouse.backend.global.auth.CustomAuthEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 그록봇 답변 수신(/api/bot/**) 전용 필터 체인(KAN-406). 기본 체인(SecurityConfig)보다 먼저 매칭된다.
 * JWT·세션은 보지 않고 Authorization: Bearer 값이 bot.reply-token과 같을 때만 ROLE_BOT을 준다.
 * 토큰 미설정이면 항상 401. 토큰 값은 로그에 남기지 않는다.
 */
@Configuration
public class BotSecurityConfig {

    private static final String ROLE = "BOT";

    @Bean
    @Order(1)
    public SecurityFilterChain botFilterChain(HttpSecurity http, CustomAuthEntryPoint customAuthEntryPoint,
                                              @Value("${bot.reply-token:}") String replyToken) throws Exception {
        http
                .securityMatcher("/api/bot/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(ROLE))
                .addFilterBefore(new BotTokenFilter(replyToken), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    // 빈으로 등록하지 않는다: 서블릿 필터로 자동 등록되면 모든 요청에 걸린다.
    static class BotTokenFilter extends OncePerRequestFilter {

        private final byte[] expected;

        BotTokenFilter(String replyToken) {
            this.expected = StringUtils.hasText(replyToken) ? replyToken.getBytes(StandardCharsets.UTF_8) : null;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (expected != null && header != null && header.startsWith("Bearer ")
                    && MessageDigest.isEqual(expected, header.substring(7).getBytes(StandardCharsets.UTF_8))) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "grok-bot", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
            }
            chain.doFilter(request, response);
        }
    }
}
