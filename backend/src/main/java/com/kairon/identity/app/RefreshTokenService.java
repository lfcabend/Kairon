package com.kairon.identity.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.kairon.identity.domain.RefreshToken;
import com.kairon.identity.repo.RefreshTokenRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes opaque refresh tokens. The raw token is a 256-bit
 * random string returned to the caller once; only its SHA-256 hash is persisted.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** The raw token (shown to the caller once) paired with its stored row. */
    public record Issued(String rawToken, RefreshToken entity) {
    }

    @Transactional
    public Issued issueNewFamily(UUID userId, String userAgent, Instant now) {
        String raw = generateRawToken();
        RefreshToken saved = repository.save(
                RefreshToken.issue(userId, hash(raw), userAgent, now.plus(properties.ttl())));
        return new Issued(raw, saved);
    }

    @Transactional
    public Issued rotate(RefreshToken current, String userAgent, Instant now) {
        current.revoke(now);
        String raw = generateRawToken();
        RefreshToken saved = repository.save(
                current.rotate(hash(raw), userAgent, now.plus(properties.ttl())));
        return new Issued(raw, saved);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByRawToken(String rawToken) {
        return repository.findByTokenHash(hash(rawToken));
    }

    /**
     * Runs in its own transaction so the revocation is durable even when the
     * caller then fails the request (reuse detection responds with 401).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId, Instant now) {
        int revoked = repository.revokeFamily(familyId, now);
        if (revoked > 0) {
            log.warn("Revoked {} refresh token(s) in family {} after reuse of a rotated token",
                    revoked, familyId);
        }
    }

    @Transactional
    public void revoke(RefreshToken token, Instant now) {
        token.revoke(now);
        repository.save(token);
    }

    @Transactional
    public void revokeAllForUser(UUID userId, Instant now) {
        repository.revokeAllByUser(userId, now);
    }

    /** Housekeeping: drop rows whose absolute expiry has passed. */
    @Scheduled(cron = "${kairon.security.refresh-token.purge-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpired() {
        long deleted = repository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }

    static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
