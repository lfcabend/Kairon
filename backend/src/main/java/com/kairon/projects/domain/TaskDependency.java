package com.kairon.projects.domain;

import java.time.Instant;
import java.util.UUID;

import com.kairon.common.id.Uuidv7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/**
 * A directed edge predecessor -> successor between two {@link ProjectTask}s.
 * Has no {@code user_id} or {@code project_id} of its own — ownership is
 * transitive through the tasks it connects (docs/DATA_MODEL.md). Create/delete
 * only, never edited (docs/milestones/M5-gantt-dependencies.md D5) — no
 * {@code @Version}/{@code updatedAt}.
 */
@Entity
@Table(name = "task_dependency")
public class TaskDependency {

    @Id
    private UUID id;

    @Column(name = "predecessor_id", nullable = false, updatable = false)
    private UUID predecessorId;

    @Column(name = "successor_id", nullable = false, updatable = false)
    private UUID successorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2, updatable = false)
    private TaskDependencyType type;

    @Column(name = "lag_days", nullable = false, updatable = false)
    private int lagDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskDependency() {
    }

    private TaskDependency(UUID predecessorId, UUID successorId, TaskDependencyType type, int lagDays) {
        this.id = Uuidv7.next();
        this.predecessorId = predecessorId;
        this.successorId = successorId;
        this.type = type;
        this.lagDays = lagDays;
    }

    public static TaskDependency create(UUID predecessorId, UUID successorId, TaskDependencyType type, int lagDays) {
        return new TaskDependency(predecessorId, successorId, type, lagDays);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPredecessorId() {
        return predecessorId;
    }

    public UUID getSuccessorId() {
        return successorId;
    }

    public TaskDependencyType getType() {
        return type;
    }

    public int getLagDays() {
        return lagDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
