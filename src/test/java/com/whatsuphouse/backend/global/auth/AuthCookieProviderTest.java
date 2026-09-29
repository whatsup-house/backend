package com.whatsuphouse.backend.global.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieProviderTest {

    private static final String ATTRS = "Path=/; Secure; HttpOnly; SameSite=Lax";

    @Test
    @DisplayName("cookie-domain 미설정이면 Domain 없는 host-only 쿠키만 발급·만료한다")
    void noDomain_hostOnlyOnly() {
        AuthCookieProvider provider = new AuthCookieProvider("");

        assertThat(provider.issue("at", "rt")).containsExactly(
                "accessToken=at; " + ATTRS,
                "refreshToken=rt; " + ATTRS);
        assertThat(provider.expire()).allMatch(c -> !c.contains("Domain=") && c.contains("Max-Age=0"))
                .hasSize(2);
    }

    @Test
    @DisplayName("cookie-domain 설정 시 발급·만료 모두 같은 Domain 을 쓰고, 이전 host-only 쿠키도 먼저 만료한다")
    void withDomain_sameDomainAndLegacyExpiry() {
        AuthCookieProvider provider = new AuthCookieProvider(".whatsup.house");

        String[] issued = provider.issue("at", "rt");
        assertThat(issued).hasSize(4);
        assertThat(issued[0]).startsWith("accessToken=; ").contains("Max-Age=0").doesNotContain("Domain=");
        assertThat(issued[1]).startsWith("refreshToken=; ").contains("Max-Age=0").doesNotContain("Domain=");
        assertThat(issued[2]).isEqualTo("accessToken=at; Path=/; Domain=.whatsup.house; Secure; HttpOnly; SameSite=Lax");
        assertThat(issued[3]).isEqualTo("refreshToken=rt; Path=/; Domain=.whatsup.house; Secure; HttpOnly; SameSite=Lax");

        String[] expired = provider.expire();
        assertThat(expired).hasSize(4).allMatch(c -> c.contains("Max-Age=0"));
        assertThat(expired[0]).doesNotContain("Domain=");
        assertThat(expired[1]).doesNotContain("Domain=");
        assertThat(expired[2]).startsWith("accessToken=; ").contains("Domain=.whatsup.house");
        assertThat(expired[3]).startsWith("refreshToken=; ").contains("Domain=.whatsup.house");
    }
}
