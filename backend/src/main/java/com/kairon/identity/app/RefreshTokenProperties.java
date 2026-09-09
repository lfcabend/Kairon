package com.kairon.identity.app;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh-token settings ({@code kairon.security.refresh-token.*}): how long an
 * opaque refresh token lives, and the attributes of the cookie the web client
 * stores it in. {@code secure} is turned off only for plain-http local dev.
 */
@ConfigurationProperties(prefix = "kairon.security.refresh-token")
public record RefreshTokenProperties(
        Duration ttl,
        Cookie cookie) {

    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofDays(30);
        }
        if (cookie == null) {
            cookie = new Cookie(null, true);
        }
    }

    public record Cookie(String name, boolean secure) {
        public Cookie {
            if (name == null || name.isBlank()) {
                name = "kairon_refresh";
            }
        }
    }
}
