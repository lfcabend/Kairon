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
 * A long-lived, named, individually revocable credential distinct from the
 * 15-minute web/Android access token and from {@link RefreshToken} (which
 * rotates on every use and is family-revoked on reuse detection — a static
 * credential fights that design). Stored only as a SHA-256 hash; the raw
 * value is shown to the user exactly once, at creation. No soft delete: a
 * revoked token is marked {@link #revokedAt}, never deleted, so "who had
 * access and when" stays auditable (docs/milestones/M10.5-mcp-server.md D2).
 */
@Entity
@Table(name = "personal_access_token")
public class PersonalAccessToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PersonalAccessToken() {
    }

    private PersonalAccessToken(UUID userId, String name, String tokenHash, Instant expiresAt) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static PersonalAccessToken issue(UUID userId, String name, String tokenHash, Instant expiresAt) {
        return new PersonalAccessToken(userId, name, tokenHash, expiresAt);
    }

    public void revoke(Instant when) {
        if (this.revokedAt == null) {
            this.revokedAt = when;
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void touchLastUsed(Instant when) {
        this.lastUsedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
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
