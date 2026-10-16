package com.kairon.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recognizes a personal-access-token (PAT) bearer credential by its fixed
 * literal prefix and, on a successful {@link PersonalAccessTokenValidator#validate},
 * sets a {@link PatPrincipal} authentication with exactly one granted
 * authority, {@code SCOPE_mcp} — the authority {@code SecurityConfig}'s
 * catch-all rule positively excludes (docs/milestones/M10.5-mcp-server.md
 * D16/D17). Ordered before {@code BearerTokenAuthenticationFilter} via the
 * paired {@link PatAwareBearerTokenResolver}, which returns {@code null} for
 * a PAT-prefixed header so the OAuth2 resource server never attempts (and
 * fails) to decode one as a JWT. A missing/invalid/revoked/expired PAT
 * leaves the security context untouched — the request falls through to
 * Spring Security's own 401, same as a missing JWT.
 */
public class PersonalAccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PersonalAccessTokenAuthenticationFilter.class);

    /** The literal prefix every issued PAT starts with — recognized here with no lookup. */
    public static final String PREFIX = "kairon_pat_";

    private final PersonalAccessTokenValidator validator;

    public PersonalAccessTokenAuthenticationFilter(PersonalAccessTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer " + PREFIX)) {
            String raw = header.substring("Bearer ".length());
            validator.validate(raw).ifPresentOrElse(pat -> {
                var authentication = new UsernamePasswordAuthenticationToken(pat, null,
                        List.of(new SimpleGrantedAuthority("SCOPE_mcp")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }, () -> log.warn("Rejected unrecognized/revoked/expired personal access token"));
        }
        chain.doFilter(request, response);
    }
}
