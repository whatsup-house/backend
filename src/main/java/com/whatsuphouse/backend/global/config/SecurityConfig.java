package com.whatsuphouse.backend.global.config;

import com.whatsuphouse.backend.global.auth.CustomAuthEntryPoint;
import com.whatsuphouse.backend.global.auth.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomAuthEntryPoint customAuthEntryPoint;

    // CORS 허용 출처. WebSocketConfig(/ws-chat)도 같은 목록을 쓴다.
    public static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "https://whatsup-house.vercel.app",
            "https://whatsup.house",
            "https://www.whatsup.house"
    );

    private static final String[] PERMIT_ALL = {
        "/api/auth/**",
        "/ws-chat", // STOMP 핸드셰이크. 인증은 CONNECT 프레임의 Authorization 헤더(ChatStompInterceptor)
        "/swagger-ui/**",
        "/api-docs/**",
        "/actuator/health"
    };

    //GET 요청만 허용
    private static final String[] PERMIT_GET = {
        "/api/gatherings/**",
        "/api/locations/**",
        "/api/users/check-nickname",
        "/api/users/check-email",
        "/api/home/carousel",
        "/api/home/curated",
        "/api/home/reviews",
        "/api/reviews",
        "/api/reviews/locate",
        "/api/jobs",
        "/api/feed"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL).permitAll()
                        .requestMatchers(HttpMethod.GET, PERMIT_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/gatherings/*/applications/guest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/applications/check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tickets/products").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
