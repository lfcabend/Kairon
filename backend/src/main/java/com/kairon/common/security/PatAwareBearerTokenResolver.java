package com.kairon.common.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Delegates to {@link DefaultBearerTokenResolver} for every ordinary request,
 * but returns {@code null} ("no bearer token found") for a {@code kairon_pat_…}
 * token — so {@code BearerTokenAuthenticationFilter} never attempts to decode
 * a PAT as a JWT. {@link PersonalAccessTokenAuthenticationFilter}, ordered
 * before that filter, is the one that actually authenticates a PAT
 * (docs/milestones/M10.5-mcp-server.md D16).
 */
public class PatAwareBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer " + PersonalAccessTokenAuthenticationFilter.PREFIX)) {
            return null;
        }
        return delegate.resolve(request);
    }
}
