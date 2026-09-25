package com.kairon.assistant.domain;

import java.time.Instant;
import java.util.HashMap;
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
 * The whole proposed plan from a {@code PROJECT_GENERATION} run
 * ({@code PROPOSED -> ACCEPTED | DISMISSED}). Unlike {@link AssistantSuggestedTask},
 * there's no per-task/per-dependency lifecycle here — {@link #plan} is the
 * entire structured-output payload (project fields + task list + dependency
 * list, cross-referenced by string {@code key}s), and accepting creates the
 * whole project, task tree, and dependency edges in one action
 * (docs/milestones/M8.5-project-generation.md D2/D3).
 */
@Entity
@Table(name = "assistant_suggested_project")
public class AssistantSuggestedProject {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> plan = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantSuggestedProjectStatus status;

    @Column(name = "accepted_project_id")
    private UUID acceptedProjectId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AssistantSuggestedProject() {
    }

    private AssistantSuggestedProject(UUID runId, UUID userId, Map<String, Object> plan) {
        this.id = Uuidv7.next();
        this.runId = runId;
        this.userId = userId;
        this.plan = new HashMap<>(plan);
        this.status = AssistantSuggestedProjectStatus.PROPOSED;
    }

    /** A fresh plan, {@code PROPOSED}, from a run's structured-output payload. */
    public static AssistantSuggestedProject propose(UUID runId, UUID userId, Map<String, Object> plan) {
        return new AssistantSuggestedProject(runId, userId, plan);
    }

    public void accept(UUID acceptedProjectId) {
        this.status = AssistantSuggestedProjectStatus.ACCEPTED;
        this.acceptedProjectId = acceptedProjectId;
    }

    public void dismiss() {
        this.status = AssistantSuggestedProjectStatus.DISMISSED;
    }

    public boolean isResolved() {
        return status != AssistantSuggestedProjectStatus.PROPOSED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Map<String, Object> getPlan() {
        return plan;
    }

    public AssistantSuggestedProjectStatus getStatus() {
        return status;
    }

    public UUID getAcceptedProjectId() {
        return acceptedProjectId;
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
