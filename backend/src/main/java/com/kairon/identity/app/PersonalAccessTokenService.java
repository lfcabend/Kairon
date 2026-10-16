package com.kairon.identity.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.PatPrincipal;
import com.kairon.common.security.PersonalAccessTokenAuthenticationFilter;
import com.kairon.common.security.PersonalAccessTokenValidator;
import com.kairon.common.security.UserId;
import com.kairon.identity.domain.PersonalAccessToken;
import com.kairon.identity.repo.PersonalAccessTokenRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, lists, and revokes personal access tokens (PATs), and is the sole
 * implementation of {@code common.security.PersonalAccessTokenValidator} —
 * the SPI that lets {@code common} recognize a PAT without importing
 * anything in {@code identity} (docs/milestones/M10.5-mcp-server.md D15).
 * Mirrors {@link RefreshTokenService}'s hashing shape (32 random bytes,
 * base64url, SHA-256 hash stored, raw value returned once) but prefixes the
 * raw value with {@link PersonalAccessTokenAuthenticationFilter#PREFIX} so
 * the filter can recognize it with no lookup, and has no rotation/family
 * concept — a PAT is long-lived until explicitly revoked (D2).
 */
@Service
public class PersonalAccessTokenService implements PersonalAccessTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(PersonalAccessTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final PersonalAccessTokenRepository repository;

    public PersonalAccessTokenService(PersonalAccessTokenRepository repository) {
        this.repository = repository;
    }

    /** A token row's web-safe shape — never the raw value (D2's own "shown once" rule). */
    public record PersonalAccessTokenView(UUID id, String name, Instant createdAt, Instant lastUsedAt,
            Instant expiresAt, boolean revoked) {

        static PersonalAccessTokenView of(PersonalAccessToken token) {
            return new PersonalAccessTokenView(token.getId(), token.getName(), token.getCreatedAt(),
                    token.getLastUsedAt(), token.getExpiresAt(), token.getRevokedAt() != null);
        }
    }

    /** The raw token (shown to the caller once) paired with its view. */
    public record Issued(String rawToken, PersonalAccessTokenView view) {
    }

    @Transactional
    public Issued issue(UserId userId, String name, Instant expiresAt) {
        String raw = PersonalAccessTokenAuthenticationFilter.PREFIX + generateRawSuffix();
        PersonalAccessToken saved = repository.save(
                PersonalAccessToken.issue(userId.value(), name, hash(raw), expiresAt));
        log.info("Issued personal access token {} name='{}' userId={}", saved.getId(), name, userId.value());
        return new Issued(raw, PersonalAccessTokenView.of(saved));
    }

    @Transactional(readOnly = true)
    public List<PersonalAccessTokenView> list(UserId userId) {
        return repository.findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId.value()).stream()
                .map(PersonalAccessTokenView::of)
                .toList();
    }

    @Transactional
    public void revoke(UserId userId, UUID id, Instant now) {
        int revoked = repository.revokeByIdAndUser(id, userId.value(), now);
        if (revoked == 0) {
            // Either it doesn't exist, isn't the caller's, or is already revoked — the
            // caller-facing distinction only matters for the first two (404), so check
            // existence-and-ownership separately rather than silently 204ing a bad id.
            repository.findByIdAndUserId(id, userId.value())
                    .orElseThrow(() -> ApiException.notFound("Personal access token not found."));
        }
        log.info("Revoked personal access token {} userId={}", id, userId.value());
    }

    @Override
    @Transactional
    public Optional<PatPrincipal> validate(String rawToken) {
        Instant now = Instant.now();
        return repository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isActive(now))
                .map(token -> {
                    token.touchLastUsed(now);
                    repository.save(token);
                    return new PatPrincipal(UserId.of(token.getUserId()), token.getId());
                });
    }

    private static String generateRawSuffix() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
