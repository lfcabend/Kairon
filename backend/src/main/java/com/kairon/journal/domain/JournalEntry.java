package com.kairon.journal.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * One journal entry: a markdown-content note for one user on one calendar
 * {@code day}, ordered within the day by {@link #position} (append-only, D4).
 * Follows the {@code todo.domain.TodoItem} conventions — a UUIDv7 id, JPA
 * optimistic locking, Hibernate-managed timestamps, a private all-args
 * constructor with a static factory, and behaviour methods rather than public
 * setters.
 *
 * <p>{@code content_tsv} is a Postgres-generated, stored column and is
 * deliberately not mapped here — search reads it via a native query
 * (see {@code journal.repo.JournalEntryRepository}).
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private int position;

    @Column(length = 200)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column
    private Short mood;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected JournalEntry() {
    }

    private JournalEntry(UUID userId, LocalDate day, int position, String title, String content, Integer mood) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.day = day;
        this.position = position;
        this.title = title;
        this.content = content == null ? "" : content;
        this.mood = clampMood(mood);
    }

    /** A fresh entry authored directly by the user. */
    public static JournalEntry create(UUID userId, LocalDate day, int position, String title, String content,
            Integer mood) {
        return new JournalEntry(userId, day, position, title, content, mood);
    }

    /** The editor always saves the whole entry together (D1). */
    public void edit(String title, String content, Integer mood) {
        this.title = title;
        this.content = content == null ? "" : content;
        this.mood = clampMood(mood);
    }

    public void softDelete(Instant when) {
        if (this.deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static Short clampMood(Integer mood) {
        if (mood == null) {
            return null;
        }
        if (mood < 1 || mood > 5) {
            throw new IllegalArgumentException("mood must be between 1 and 5");
        }
        return mood.shortValue();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Integer getMood() {
        return mood == null ? null : mood.intValue();
    }

    public Instant getDeletedAt() {
        return deletedAt;
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
