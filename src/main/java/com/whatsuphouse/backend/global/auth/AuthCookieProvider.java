package com.whatsuphouse.backend.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * accessToken/refreshToken 인증 쿠키의 Set-Cookie 값을 만든다.
 * 발급·만료 쿠키의 Domain 이 어긋나면 삭제가 동작하지 않으므로 모든 인증 쿠키는 여기서만 만든다.
 */
@Component
public class AuthCookieProvider {

    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";

    private static final long SESSION = -1;
    private static final long EXPIRED = 0;

    /** prod: .whatsup.house (www 서버도 쿠키를 받도록). local 등 미설정 시 host-only 쿠키. */
    private final String cookieDomain;

    public AuthCookieProvider(@Value("${app.cookie-domain:}") String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    /** 로그인·토큰 갱신 응답의 Set-Cookie 값들. */
    public String[] issue(String accessToken, String refreshToken) {
        return withHostOnlyExpiry(
                cookie(ACCESS_TOKEN, accessToken, cookieDomain, SESSION),
                cookie(REFRESH_TOKEN, refreshToken, cookieDomain, SESSION));
    }

    /** 로그아웃·회원탈퇴 응답의 Set-Cookie 값들. */
    public String[] expire() {
        return withHostOnlyExpiry(
                cookie(ACCESS_TOKEN, "", cookieDomain, EXPIRED),
                cookie(REFRESH_TOKEN, "", cookieDomain, EXPIRED));
    }

    /*
     * Domain 도입 전에 발급된 host-only 쿠키도 만료시킨다. 남아 있으면 같은 이름의 Domain 쿠키보다 먼저 전송돼
     * (RFC 6265: 생성이 이른 쿠키가 앞) 서버가 옛 토큰을 읽고, 회전된 refreshToken 불일치로 로그인이 풀린다.
     */
    private String[] withHostOnlyExpiry(String accessCookie, String refreshCookie) {
        if (!StringUtils.hasText(cookieDomain)) {
            return new String[]{accessCookie, refreshCookie};
        }
        return new String[]{
                cookie(ACCESS_TOKEN, "", "", EXPIRED),
                cookie(REFRESH_TOKEN, "", "", EXPIRED),
                accessCookie,
                refreshCookie
        };
    }

    private String cookie(String name, String value, String domain, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .domain(domain)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
