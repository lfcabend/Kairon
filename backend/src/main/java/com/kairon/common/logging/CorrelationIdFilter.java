package com.kairon.common.logging;

import java.io.IOException;

import com.kairon.common.id.Uuidv7;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id and exposes it two ways:
 *
 * <ul>
 *   <li>in the SLF4J {@link MDC} under {@code correlationId}, so every log line
 *       emitted while handling the request carries it — the plain console pattern
 *       prints it (see {@code logging.pattern.correlation} in {@code
 *       application.yml}) and the {@code prod} profile's ECS JSON encoder emits it
 *       as a field;</li>
 *   <li>in the {@code X-Request-Id} response header, so a client (or an operator
 *       reading a HAR / curl output) can tie a response back to the server logs.</li>
 * </ul>
 *
 * <p>An inbound {@code X-Request-Id} is reused when it is short and made only of
 * unreserved token characters; anything else (or nothing) yields a fresh UUIDv7.
 * Registered at {@link Ordered#HIGHEST_PRECEDENCE} so it wraps the Spring Security
 * filter chain and the {@code RateLimitFilter} — their log output is correlated too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String inbound) {
        if (inbound != null && !inbound.isBlank() && inbound.length() <= MAX_LENGTH
                && inbound.chars().allMatch(CorrelationIdFilter::isTokenChar)) {
            return inbound;
        }
        return Uuidv7.next().toString();
    }

    private static boolean isTokenChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
    }
}
