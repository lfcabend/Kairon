package com.kairon.projects.domain;

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
 * A small personal project, optionally grouped under a {@link ProjectCategory}.
 * Follows the {@code todo}/{@code journal} entity conventions — a UUIDv7 id,
 * JPA optimistic locking, Hibernate-managed timestamps, a private all-args
 * constructor with a static factory, and behaviour methods rather than public
 * setters.
 *
 * <p>{@link #priorityRank} is a manual, drag-ordered rank (D18) — it only
 * changes via {@code ProjectService.reorder}, never through {@link #edit}.
 */
@Entity
@Table(name = "project")
public class Project {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private ProjectSize size;

    @Column(name = "priority_rank", nullable = false)
    private int priorityRank;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "actual_start")
    private LocalDate actualStart;

    @Column(name = "actual_end")
    private LocalDate actualEnd;

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

    protected Project() {
    }

    private Project(UUID userId, UUID categoryId, String name, String description, String color,
            ProjectSize size, int priorityRank, LocalDate startDate, LocalDate endDate) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.status = ProjectStatus.PLANNING;
        this.size = size;
        this.priorityRank = priorityRank;
        this.color = color;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** A fresh project, {@code PLANNING}, appended to the bottom of the priority rank (D20). */
    public static Project create(UUID userId, UUID categoryId, String name, String description, String color,
            ProjectSize size, int priorityRank, LocalDate startDate, LocalDate endDate) {
        return new Project(userId, categoryId, name, description, color, size, priorityRank, startDate, endDate);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void editDescription(String description) {
        this.description = description;
    }

    public void recolor(String hex) {
        this.color = hex;
    }

    public void recategorize(UUID categoryId) {
        this.categoryId = categoryId;
    }

    /** Nullable — clears sizing. */
    public void resize(ProjectSize size) {
        this.size = size;
    }

    /** Set only by {@code ProjectService.reorder} (D20) — a plain sparse position. */
    public void moveTo(int priorityRank) {
        this.priorityRank = priorityRank;
    }

    public void reschedule(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void markActualStart(LocalDate actualStart) {
        this.actualStart = actualStart;
    }

    public void markActualEnd(LocalDate actualEnd) {
        this.actualEnd = actualEnd;
    }

    public void changeStatus(ProjectStatus status) {
        this.status = status;
    }

    /** Whole-form overwrite (D15) of every field the edit dialog owns, except {@link #priorityRank} (D18). */
    public void edit(UUID categoryId, String name, String description, ProjectStatus status, ProjectSize size,
            String color, LocalDate startDate, LocalDate endDate, LocalDate actualStart, LocalDate actualEnd) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.size = size;
        this.color = color;
        this.startDate = startDate;
        this.endDate = endDate;
        this.actualStart = actualStart;
        this.actualEnd = actualEnd;
    }

    public void softDelete(Instant when) {
        if (this.deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public ProjectSize getSize() {
        return size;
    }

    public int getPriorityRank() {
        return priorityRank;
    }

    public String getColor() {
        return color;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getActualStart() {
        return actualStart;
    }

    public LocalDate getActualEnd() {
        return actualEnd;
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
