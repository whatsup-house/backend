package com.whatsuphouse.backend.global.auth;

import com.whatsuphouse.backend.global.exception.CustomException;
import com.whatsuphouse.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "typ";
    private static final String CHAT_SOCKET_TYPE = "chat-socket";

    private final SecretKey signingKey;
    private final long expiration;
    private final long refreshExpiration;
    private final long chatSocketExpiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration}") long expiration,
                            @Value("${jwt.refresh-expiration}") long refreshExpiration,
                            @Value("${jwt.chat-socket-expiration}") long chatSocketExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
        this.chatSocketExpiration = chatSocketExpiration;
    }

    public String generateAccessToken(UserPrincipal principal) {
        return userToken(principal, expiration).compact();
    }

    /** STOMP CONNECT 전용 단기 토큰(typ=chat-socket). JwtAuthFilter 는 이 토큰을 REST 인증에 쓰지 않는다. */
    public String generateChatSocketToken(UserPrincipal principal) {
        return userToken(principal, chatSocketExpiration).claim(TYPE_CLAIM, CHAT_SOCKET_TYPE).compact();
    }

    private JwtBuilder userToken(UserPrincipal principal, long ttl) {
        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("email", principal.getEmail())
                .claim("isAdmin", principal.isAdmin())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(signingKey);
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(signingKey)
                .compact();
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public long getChatSocketExpiration() {
        return chatSocketExpiration;
    }

    public void validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    /** validateToken 통과한 토큰 전제. */
    public boolean isChatSocketToken(String token) {
        return CHAT_SOCKET_TYPE.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UserPrincipal getUserPrincipal(String token) {
        Claims claims = parseClaims(token);

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        boolean isAdmin = Boolean.TRUE.equals(claims.get("isAdmin", Boolean.class));

        return new UserPrincipal(userId, email, isAdmin);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
