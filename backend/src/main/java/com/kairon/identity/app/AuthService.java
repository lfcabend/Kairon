package com.kairon.identity.app;

import java.time.Clock;
import java.time.Instant;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.app.AuthCommands.AuthResult;
import com.kairon.identity.app.AuthCommands.LoginCommand;
import com.kairon.identity.app.AuthCommands.RegisterCommand;
import com.kairon.identity.app.JwtAccessTokenService.MintedAccessToken;
import com.kairon.identity.domain.AppUser;
import com.kairon.identity.domain.RefreshToken;
import com.kairon.identity.domain.UserStatus;
import com.kairon.identity.repo.AppUserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh-token rotation, and logout. Every method that
 * mints credentials returns an {@link AuthResult}; the controller is responsible
 * for turning the refresh token into a cookie.
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final AppUserRepository users;
    private final RefreshTokenService refreshTokens;
    private final JwtAccessTokenService accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AuthService(AppUserRepository users, RefreshTokenService refreshTokens,
            JwtAccessTokenService accessTokens, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterCommand command, String userAgent) {
        String email = AppUser.normalizeEmail(command.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with that email already exists.");
        }
        AppUser user = users.save(AppUser.register(
                email,
                passwordEncoder.encode(command.rawPassword()),
                command.displayName().trim(),
                command.timezone()));
        return issueFor(user, userAgent);
    }

    @Transactional
    public AuthResult login(LoginCommand command, String userAgent) {
        AppUser user = users.findByEmail(AppUser.normalizeEmail(command.email()))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(command.rawPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("This account is not active.");
        }
        return issueFor(user, userAgent);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Missing refresh token.");
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByRawToken(rawRefreshToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token."));

        if (current.isRevoked()) {
            // A rotated-away token is being replayed: assume theft, kill the family.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw ApiException.unauthorized("Refresh token has been revoked.");
        }
        if (!current.isActive(now)) {
            throw ApiException.unauthorized("Refresh token has expired.");
        }

        AppUser user = users.findById(current.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists."));

        RefreshTokenService.Issued rotated = refreshTokens.rotate(current, userAgent, now);
        MintedAccessToken access = accessTokens.mint(UserId.of(user.getId()), now);
        return new AuthResult(access.value(), access.ttl(),
                rotated.rawToken(), rotated.entity().getExpiresAt(), AccountViews.of(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByRawToken(rawRefreshToken)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> refreshTokens.revoke(token, clock.instant()));
    }

    @Transactional
    public void logoutAll(UserId userId) {
        refreshTokens.revokeAllForUser(userId.value(), clock.instant());
    }

    private AuthResult issueFor(AppUser user, String userAgent) {
        Instant now = clock.instant();
        RefreshTokenService.Issued refresh = refreshTokens.issueNewFamily(user.getId(), userAgent, now);
        MintedAccessToken access = accessTokens.mint(UserId.of(user.getId()), now);
        return new AuthResult(access.value(), access.ttl(),
                refresh.rawToken(), refresh.entity().getExpiresAt(), AccountViews.of(user));
    }
}
