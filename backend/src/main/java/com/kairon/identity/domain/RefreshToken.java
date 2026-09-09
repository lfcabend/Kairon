package com.kairon.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.kairon.common.id.Uuidv7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/**
 * One issued refresh token, stored only as a SHA-256 hash. Tokens rotate on every
 * use: a successful refresh revokes this row and issues a fresh one in the same
 * {@link #familyId}. Presenting an already-revoked token is treated as theft and
 * the whole family is revoked (see {@code AuthService#refresh}).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(UUID userId, String tokenHash, UUID familyId, String userAgent, Instant expiresAt) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
    }

    /** Starts a brand-new rotation family. */
    public static RefreshToken issue(UUID userId, String tokenHash, String userAgent, Instant expiresAt) {
        return new RefreshToken(userId, tokenHash, Uuidv7.next(), userAgent, expiresAt);
    }

    /** Continues an existing rotation family (called when an old token is exchanged). */
    public RefreshToken rotate(String newTokenHash, String userAgent, Instant expiresAt) {
        return new RefreshToken(this.userId, newTokenHash, this.familyId, userAgent, expiresAt);
    }

    public void revoke(Instant when) {
        if (this.revokedAt == null) {
            this.revokedAt = when;
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
