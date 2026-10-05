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
 * The whole proposed diff from a {@code PROJECT_EDIT} run
 * ({@code PROPOSED -> ACCEPTED | DISMISSED}), scoped to the one existing
 * {@link #projectId} it targets. Unlike {@link AssistantSuggestedProject},
 * accepting never creates a new project — it mutates {@link #projectId} in
 * place, so there's no {@code acceptedProjectId} to track (docs/milestones/
 * M9.5-ai-project-editing.md D2/D3).
 */
@Entity
@Table(name = "assistant_suggested_project_edit")
public class AssistantSuggestedProjectEdit {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> diff = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantSuggestedProjectEditStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AssistantSuggestedProjectEdit() {
    }

    private AssistantSuggestedProjectEdit(UUID runId, UUID userId, UUID projectId, Map<String, Object> diff) {
        this.id = Uuidv7.next();
        this.runId = runId;
        this.userId = userId;
        this.projectId = projectId;
        this.diff = new HashMap<>(diff);
        this.status = AssistantSuggestedProjectEditStatus.PROPOSED;
    }

    /** A fresh diff, {@code PROPOSED}, from a run's structured-output payload. */
    public static AssistantSuggestedProjectEdit propose(UUID runId, UUID userId, UUID projectId,
            Map<String, Object> diff) {
        return new AssistantSuggestedProjectEdit(runId, userId, projectId, diff);
    }

    public void accept() {
        this.status = AssistantSuggestedProjectEditStatus.ACCEPTED;
    }

    public void dismiss() {
        this.status = AssistantSuggestedProjectEditStatus.DISMISSED;
    }

    public boolean isResolved() {
        return status != AssistantSuggestedProjectEditStatus.PROPOSED;
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

    public UUID getProjectId() {
        return projectId;
    }

    public Map<String, Object> getDiff() {
        return diff;
    }

    public AssistantSuggestedProjectEditStatus getStatus() {
        return status;
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
