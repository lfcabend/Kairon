package com.kairon.identity.app;

import java.time.Duration;
import java.time.Instant;

import com.kairon.identity.api.UserAccountView;

/** Inputs and outputs of {@link AuthService}, free of any web/JPA types. */
public final class AuthCommands {

    private AuthCommands() {
    }

    public record RegisterCommand(String email, String rawPassword, String displayName, String timezone) {
    }

    public record LoginCommand(String email, String rawPassword) {
    }

    /**
     * The freshly minted credentials. {@code rawRefreshToken} is the only time the
     * opaque token is available in clear — the controller drops it into a cookie.
     */
    public record AuthResult(
            String accessToken,
            Duration accessTokenTtl,
            String rawRefreshToken,
            Instant refreshTokenExpiresAt,
            UserAccountView user) {
    }
}
