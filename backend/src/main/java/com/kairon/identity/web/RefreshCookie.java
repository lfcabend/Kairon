package com.kairon.identity.web;

import java.time.Duration;
import java.util.Optional;

import com.kairon.identity.app.RefreshTokenProperties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and reads the refresh-token cookie: {@code HttpOnly}, {@code Secure}
 * (off only for local http dev), {@code SameSite=Strict}, and scoped to
 * {@code <context-path>/api/v1/auth} so it is never sent to any other route.
 * See docs/DESIGN.md §6.
 */
@Component
public class RefreshCookie {

    private final RefreshTokenProperties properties;
    private final String path;

    public RefreshCookie(RefreshTokenProperties properties,
            @Value("${server.servlet.context-path:}") String contextPath) {
        this.properties = properties;
        this.path = contextPath + "/api/v1/auth";
    }

    public String name() {
        return properties.cookie().name();
    }

    public ResponseCookie issue(String rawToken) {
        return base(rawToken)
                .maxAge(properties.ttl())
                .build();
    }

    public ResponseCookie clear() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name().equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name(), value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite("Strict")
                .path(path);
    }
}
