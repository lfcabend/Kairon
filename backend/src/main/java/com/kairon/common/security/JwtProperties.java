package com.kairon.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access-token settings ({@code kairon.security.jwt.*}). The signing secret is any
 * string of reasonable entropy — {@link CryptoConfig} runs it through SHA-256 to
 * derive the 256-bit key HS256 requires, so operators need not size it exactly.
 */
@ConfigurationProperties(prefix = "kairon.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("kairon.security.jwt.secret must be set");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "kairon";
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
    }
}
