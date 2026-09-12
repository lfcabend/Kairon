package com.kairon.projects.domain;

import java.time.Instant;
import java.util.UUID;

import com.kairon.common.id.Uuidv7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A user-managed "epic"-like label a {@link Project} may be grouped under
 * (D12) — its own identity with a name, colour, and display order, not a
 * plain string field on {@code project}. No {@code deletedAt}: deleting a
 * category is a real hard delete (D14).
 */
@Entity
@Table(name = "project_category")
public class ProjectCategory {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    private int position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ProjectCategory() {
    }

    private ProjectCategory(UUID userId, String name, String color, int position) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.name = name;
        this.color = color;
        this.position = position;
    }

    public static ProjectCategory create(UUID userId, String name, String color, int position) {
        return new ProjectCategory(userId, name, color, position);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void recolor(String hex) {
        this.color = hex;
    }

    public void moveTo(int position) {
        this.position = position;
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

    public String getColor() {
        return color;
    }

    public int getPosition() {
        return position;
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
