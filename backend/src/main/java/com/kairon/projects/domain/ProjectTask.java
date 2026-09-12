package com.kairon.projects.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
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
 * One task (or, one level down, subtask) inside a {@link Project}. Has no
 * {@code user_id} of its own — a task is owned transitively through its
 * project (docs/DATA_MODEL.md); every query resolves the project under the
 * caller first. The at-most-2-level rule is enforced by
 * {@code ProjectTaskService}, not here or in the schema.
 */
@Entity
@Table(name = "project_task")
public class ProjectTask {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Column(nullable = false, length = 300)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectTaskStatus status;

    @Column(name = "is_milestone", nullable = false)
    private boolean isMilestone;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_end")
    private LocalDate plannedEnd;

    @Column(name = "estimate_hours")
    private BigDecimal estimateHours;

    @Column(name = "actual_hours")
    private BigDecimal actualHours;

    // Stored as SMALLINT (docs/DATA_MODEL.md); exposed as int for ergonomics.
    @Column(name = "progress_percent", nullable = false)
    private short progressPercent;

    @Column(nullable = false)
    private int position;

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

    protected ProjectTask() {
    }

    private ProjectTask(UUID projectId, UUID parentTaskId, String name, String description, int position) {
        this.id = Uuidv7.next();
        this.projectId = projectId;
        this.parentTaskId = parentTaskId;
        this.name = name;
        this.description = description;
        this.status = ProjectTaskStatus.TODO;
        this.isMilestone = false;
        this.progressPercent = 0;
        this.position = position;
    }

    /** A fresh task, {@code TODO}, 0% progress. */
    public static ProjectTask create(UUID projectId, UUID parentTaskId, String name, String description,
            int position) {
        return new ProjectTask(projectId, parentTaskId, name, description, position);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void editDescription(String description) {
        this.description = description;
    }

    public void changeStatus(ProjectTaskStatus status) {
        this.status = status;
    }

    public void setEstimateHours(BigDecimal estimateHours) {
        this.estimateHours = estimateHours;
    }

    public void setActualHours(BigDecimal actualHours) {
        this.actualHours = actualHours;
    }

    public void setProgress(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
        this.progressPercent = (short) percent;
    }

    /** Depth validated by the service (§4.4) before this is called; the entity just assigns. */
    public void reparent(UUID newParentTaskId) {
        this.parentTaskId = newParentTaskId;
    }

    public void markMilestone(LocalDate date) {
        Objects.requireNonNull(date, "date");
        this.isMilestone = true;
        this.plannedStart = date;
        this.plannedEnd = date;
    }

    public void unmarkMilestone() {
        this.isMilestone = false;
    }

    /** Rejects a call that would leave {@code isMilestone=true} with unequal dates — use {@link #markMilestone} instead. */
    public void reschedule(LocalDate start, LocalDate end) {
        if (isMilestone && !Objects.equals(start, end)) {
            throw new IllegalArgumentException(
                    "A milestone task's planned start and end must match; use markMilestone instead.");
        }
        this.plannedStart = start;
        this.plannedEnd = end;
    }

    public void moveTo(int position) {
        this.position = position;
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

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getParentTaskId() {
        return parentTaskId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectTaskStatus getStatus() {
        return status;
    }

    public boolean isMilestone() {
        return isMilestone;
    }

    public LocalDate getPlannedStart() {
        return plannedStart;
    }

    public LocalDate getPlannedEnd() {
        return plannedEnd;
    }

    public BigDecimal getEstimateHours() {
        return estimateHours;
    }

    public BigDecimal getActualHours() {
        return actualHours;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public int getPosition() {
        return position;
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
