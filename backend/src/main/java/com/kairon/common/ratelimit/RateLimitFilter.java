package com.kairon.common.ratelimit;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects bursts against the configured auth paths with {@code 429} and an
 * {@code application/problem+json} body. One in-memory token bucket per
 * (client IP, request path); refills continuously at the configured rate.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = contextRelativePath(request);
        return properties.paths().stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(key(request), k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(response, probe.getNanosToWaitForRefill());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.capacity())
                .refillGreedy(properties.capacity(), properties.refillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String key(HttpServletRequest request) {
        return clientIp(request) + "|" + contextRelativePath(request);
    }

    /**
     * {@code getRequestURI()} includes the servlet context path (e.g. {@code /kairon}),
     * but {@code kairon.security.rate-limit.paths} is written context-relative to match
     * how Spring Security's own request matchers behave — strip it so both agree.
     */
    private static String contextRelativePath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long nanosToWait) throws IOException {
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanosToWait));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Retry in about " + retryAfterSeconds + "s.");
        problem.setType(java.net.URI.create("https://kairon.app/problems/rate-limited"));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
