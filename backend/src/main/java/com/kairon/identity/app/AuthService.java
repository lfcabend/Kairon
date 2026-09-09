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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
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
            log.warn("Registration rejected: email already in use ({})", email);
            throw ApiException.conflict("An account with that email already exists.");
        }
        AppUser user = users.save(AppUser.register(
                email,
                passwordEncoder.encode(command.rawPassword()),
                command.displayName().trim(),
                command.timezone()));
        log.info("Registered new account userId={} email={}", user.getId(), email);
        return issueFor(user, userAgent);
    }

    @Transactional
    public AuthResult login(LoginCommand command, String userAgent) {
        AppUser user = users.findByEmail(AppUser.normalizeEmail(command.email()))
                .orElseThrow(() -> {
                    log.warn("Login failed: no account for email={}", command.email());
                    return ApiException.unauthorized(INVALID_CREDENTIALS);
                });
        if (!passwordEncoder.matches(command.rawPassword(), user.getPasswordHash())) {
            log.warn("Login failed: bad password userId={}", user.getId());
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login failed: account not active userId={} status={}", user.getId(), user.getStatus());
            throw ApiException.unauthorized("This account is not active.");
        }
        log.info("Login succeeded userId={}", user.getId());
        return issueFor(user, userAgent);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            log.warn("Refresh failed: no token presented");
            throw ApiException.unauthorized("Missing refresh token.");
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByRawToken(rawRefreshToken)
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token not recognised");
                    return ApiException.unauthorized("Invalid refresh token.");
                });

        if (current.isRevoked()) {
            // A rotated-away token is being replayed: assume theft, kill the family.
            log.warn("Refresh reuse detected: revoking token family {} userId={}",
                    current.getFamilyId(), current.getUserId());
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw ApiException.unauthorized("Refresh token has been revoked.");
        }
        if (!current.isActive(now)) {
            log.warn("Refresh failed: token expired userId={}", current.getUserId());
            throw ApiException.unauthorized("Refresh token has expired.");
        }

        AppUser user = users.findById(current.getUserId())
                .orElseThrow(() -> {
                    log.warn("Refresh failed: account {} no longer exists", current.getUserId());
                    return ApiException.unauthorized("Account no longer exists.");
                });

        RefreshTokenService.Issued rotated = refreshTokens.rotate(current, userAgent, now);
        MintedAccessToken access = accessTokens.mint(UserId.of(user.getId()), now);
        log.info("Refresh rotated userId={}", user.getId());
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
                .ifPresent(token -> {
                    refreshTokens.revoke(token, clock.instant());
                    log.info("Logout: revoked refresh token userId={}", token.getUserId());
                });
    }

    @Transactional
    public void logoutAll(UserId userId) {
        refreshTokens.revokeAllForUser(userId.value(), clock.instant());
        log.info("Logout-all: revoked every refresh token userId={}", userId.value());
    }

    private AuthResult issueFor(AppUser user, String userAgent) {
        Instant now = clock.instant();
        RefreshTokenService.Issued refresh = refreshTokens.issueNewFamily(user.getId(), userAgent, now);
        MintedAccessToken access = accessTokens.mint(UserId.of(user.getId()), now);
        return new AuthResult(access.value(), access.ttl(),
                refresh.rawToken(), refresh.entity().getExpiresAt(), AccountViews.of(user));
    }
}
