package com.kairon.todo.domain;

import java.time.Instant;
import java.time.LocalDate;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One todo item: a single line of intent for one user on one calendar {@code day}.
 * Follows the {@code identity} entity conventions — a UUIDv7 id, JPA optimistic
 * locking, Hibernate-managed timestamps, a private all-args constructor with
 * static factories, and behaviour methods rather than public setters.
 *
 * <p>{@code deletedAt} is a soft delete; every list query filters it out. A
 * rolled-forward item keeps {@link #rolledOverFromId} pointing at the item it
 * carries (docs/DATA_MODEL.md, docs/DESIGN.md §2.1).
 */
@Entity
@Table(name = "todo_item")
public class TodoItem {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false, length = 500)
    private String title;

    @Column
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoStatus status;

    // Stored as SMALLINT (docs/DATA_MODEL.md); exposed as int for ergonomics.
    @Column(nullable = false)
    private short priority;

    @Column(nullable = false)
    private int position;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    @Column(name = "source_project_task_id")
    private UUID sourceProjectTaskId;

    @Column(name = "rolled_over_from_id")
    private UUID rolledOverFromId;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    protected TodoItem() {
    }

    private TodoItem(UUID userId, LocalDate day, String title, String notes, int priority,
            int position, Integer estimateMinutes, UUID sourceProjectTaskId, UUID rolledOverFromId) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.day = day;
        this.title = title;
        this.notes = notes;
        this.status = TodoStatus.OPEN;
        this.priority = (short) priority;
        this.position = position;
        this.estimateMinutes = estimateMinutes;
        this.sourceProjectTaskId = sourceProjectTaskId;
        this.rolledOverFromId = rolledOverFromId;
    }

    /** A fresh item, {@code OPEN}, authored directly by the user. */
    public static TodoItem create(UUID userId, LocalDate day, String title, String notes, int priority,
            int position, Integer estimateMinutes, UUID sourceProjectTaskId) {
        return new TodoItem(userId, day, title, notes, clampPriority(priority), position,
                estimateMinutes, sourceProjectTaskId, null);
    }

    /**
     * A copy of {@code source} placed on {@code toDay}: same title, notes,
     * priority, estimate and project link, with {@link #rolledOverFromId} set to
     * the source id so the carry chain stays traceable.
     */
    public static TodoItem rolledFrom(TodoItem source, LocalDate toDay, int position) {
        return new TodoItem(source.userId, toDay, source.title, source.notes, source.priority,
                position, source.estimateMinutes, source.sourceProjectTaskId, source.id);
    }

    public void rename(String title) {
        this.title = title;
    }

    public void editNotes(String notes) {
        this.notes = notes;
    }

    public void reprioritise(int priority) {
        this.priority = (short) clampPriority(priority);
    }

    public void estimate(Integer minutes) {
        this.estimateMinutes = minutes;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public void complete(Instant when) {
        this.status = TodoStatus.DONE;
        this.completedAt = when;
    }

    public void reopen() {
        this.status = TodoStatus.OPEN;
        this.completedAt = null;
    }

    public void cancel() {
        this.status = TodoStatus.CANCELLED;
    }

    public void softDelete(Instant when) {
        if (this.deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static int clampPriority(int priority) {
        if (priority < 0 || priority > 3) {
            throw new IllegalArgumentException("priority must be between 0 and 3");
        }
        return priority;
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

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public TodoStatus getStatus() {
        return status;
    }

    public int getPriority() {
        return priority;
    }

    public int getPosition() {
        return position;
    }

    public Integer getEstimateMinutes() {
        return estimateMinutes;
    }

    public UUID getSourceProjectTaskId() {
        return sourceProjectTaskId;
    }

    public UUID getRolledOverFromId() {
        return rolledOverFromId;
    }

    public Instant getCompletedAt() {
        return completedAt;
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
