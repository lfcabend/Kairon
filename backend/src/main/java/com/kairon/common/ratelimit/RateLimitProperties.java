package com.kairon.common.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fixed-window-ish token-bucket limits for the sensitive auth endpoints
 * ({@code kairon.security.rate-limit.*}). Buckets are per client IP + path and
 * held in memory — adequate for a single-instance personal deployment.
 */
@ConfigurationProperties(prefix = "kairon.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        long capacity,
        Duration refillPeriod,
        List<String> paths) {

    public RateLimitProperties {
        if (capacity <= 0) {
            capacity = 10;
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            refillPeriod = Duration.ofMinutes(1);
        }
        paths = paths == null ? List.of() : List.copyOf(paths);
    }
}
