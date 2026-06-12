package com.whatsuphouse.backend.global.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatsuphouse.backend.global.common.ApiResult;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 공개 인증·신청 API의 IP 기준 호출 횟수 제한 필터. (KAN-233)
 *
 * Redis INCR + TTL 고정 윈도우 방식으로 제한하며,
 * Redis 장애 시에는 서비스 가용성을 우선해 제한 없이 통과시킨다(fail-open).
 * 서버가 Caddy 리버스 프록시 뒤에 있으므로 클라이언트 IP는 X-Forwarded-For에서 읽는다.
 *
 * @Component로 만들면 @WebMvcTest 슬라이스가 Filter를 스캔해 Redis 빈 부재로 깨지므로
 * RateLimitConfig의 FilterRegistrationBean으로만 등록한다.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String pathPattern, String keyPrefix, int limit, Duration window) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", "login", 5, Duration.ofMinutes(1)),
            new Rule("POST", "/api/auth/password-reset/request", "password-reset", 3, Duration.ofHours(1)),
            new Rule("POST", "/api/gatherings/*/applications/guest", "guest-apply", 10, Duration.ofHours(1))
    );

    private static final String KEY_PREFIX = "rate-limit:";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    // INCR과 EXPIRE를 한 번에 실행해 원자성을 보장한다.
    // 분리하면 EXPIRE 실패 시 TTL 없는 키가 남아 해당 IP가 영구 차단될 수 있다.
    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule rule = findRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isLimitExceeded(rule, resolveClientIp(request))) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Rule findRule(HttpServletRequest request) {
        return RULES.stream()
                .filter(rule -> rule.method().equalsIgnoreCase(request.getMethod())
                        && pathMatcher.match(rule.pathPattern(), request.getRequestURI()))
                .findFirst()
                .orElse(null);
    }

    private boolean isLimitExceeded(Rule rule, String clientIp) {
        String key = KEY_PREFIX + rule.keyPrefix() + ":" + clientIp;
        try {
            Long count = redisTemplate.execute(
                    INCR_WITH_TTL, List.of(key), String.valueOf(rule.window().toSeconds()));
            return count != null && count > rule.limit();
        } catch (Exception e) {
            log.warn("[RateLimit] Redis 연산 실패 - 제한 없이 통과: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 서버는 Caddy 단일 리버스 프록시 뒤에 있다. Caddy reverse_proxy는 직접 연결된
     * 클라이언트의 실제 IP를 X-Forwarded-For 맨 끝에 append하므로, 클라이언트가 위조해
     * 보낸 XFF 값들 뒤에 신뢰 가능한 실제 IP가 붙는다. 따라서 첫 값이 아니라 마지막 값을 쓴다.
     * (프록시 단을 늘리면 이 전제를 재검토해야 한다.)
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResult.fail(ErrorCode.TOO_MANY_REQUESTS.getMessage()));
    }
}
