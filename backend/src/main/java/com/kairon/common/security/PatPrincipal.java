package com.kairon.common.security;

import java.util.UUID;

/**
 * The authenticated principal for a personal-access-token (PAT) request —
 * the {@code SecurityContext}'s principal once {@link PersonalAccessTokenAuthenticationFilter}
 * validates a {@code kairon_pat_…} bearer token, parallel to a {@code Jwt}
 * principal for the ordinary web/Android path. Carries only what
 * {@link CurrentUserArgumentResolver} and the rest of the app need: whose
 * token this is, and the token row's own id (for audit/`lastUsedAt`
 * purposes — never used for authorization, which is scoped entirely by
 * {@code userId}). Added for M10.5 (docs/milestones/M10.5-mcp-server.md D15).
 */
public record PatPrincipal(UserId userId, UUID tokenId) {
}
