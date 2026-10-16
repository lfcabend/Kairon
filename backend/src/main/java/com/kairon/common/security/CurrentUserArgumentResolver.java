package com.kairon.common.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Resolves {@code @CurrentUser UserId} parameters from either a {@code Jwt}
 * principal (the ordinary access-token {@code sub} claim) or a
 * {@link PatPrincipal} (a personal-access-token request, M10.5). No database
 * lookup for either shape: a JWT is proof of identity by construction, and a
 * {@code PatPrincipal} was already resolved by a real lookup in
 * {@link PersonalAccessTokenAuthenticationFilter} before it ever reached the
 * security context. Reusing this one resolver for both credential types
 * means no existing controller method signature needs to change to also
 * accept PAT-authenticated calls (docs/milestones/M10.5-mcp-server.md D16).
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserId.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null || !authentication.isAuthenticated() ? null
                : authentication.getPrincipal();
        return switch (principal) {
            case Jwt jwt -> resolveFromJwt(jwt);
            case PatPrincipal pat -> pat.userId();
            default -> throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        };
    }

    private UserId resolveFromJwt(Jwt jwt) {
        try {
            return UserId.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Malformed subject claim");
        }
    }
}
