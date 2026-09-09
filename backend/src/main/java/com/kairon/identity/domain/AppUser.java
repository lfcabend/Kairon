package com.kairon.identity.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.kairon.common.id.Uuidv7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A registered account. Email is the login identifier, stored lower-cased and
 * unique case-folded. {@code preferences} is a schema-less JSON blob for UI
 * settings (theme, default landing screen) and, from M8, per-feature assistant
 * opt-ins.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> preferences = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AppUser() {
    }

    private AppUser(String email, String passwordHash, String displayName, String timezone) {
        this.id = Uuidv7.next();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.timezone = timezone;
        this.status = UserStatus.ACTIVE;
    }

    /** Creates an ACTIVE account. The password must already be hashed. */
    public static AppUser register(String email, String passwordHash, String displayName, String timezone) {
        return new AppUser(email, passwordHash, displayName, timezone);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void changeTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void replacePreferences(Map<String, Object> preferences) {
        this.preferences = preferences == null ? new HashMap<>() : new HashMap<>(preferences);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Map<String, Object> getPreferences() {
        return preferences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
