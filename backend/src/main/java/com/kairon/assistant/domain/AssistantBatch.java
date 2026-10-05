package com.kairon.assistant.domain;

import java.time.Instant;
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
 * Tracks one Anthropic Message Batches API submission for M9's scheduled
 * summary sweep — one row per scheduler firing (docs/milestones/
 * M10-journal-reflection.md D20/D21), independent of the {@code assistant_run}
 * state machine. No soft delete, matching every other {@code assistant_*}
 * table's hard-delete-only convention.
 */
@Entity
@Table(name = "assistant_batch")
public class AssistantBatch {

    @Id
    private UUID id;

    @Column(name = "anthropic_batch_id", nullable = false, updatable = false, length = 100)
    private String anthropicBatchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AssistantRunKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantBatchStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AssistantBatch() {
    }

    private AssistantBatch(String anthropicBatchId, AssistantRunKind kind) {
        this.id = Uuidv7.next();
        this.anthropicBatchId = anthropicBatchId;
        this.kind = kind;
        this.status = AssistantBatchStatus.IN_PROGRESS;
        this.submittedAt = Instant.now();
    }

    /** A freshly-submitted batch, {@code IN_PROGRESS}. */
    public static AssistantBatch pending(String anthropicBatchId, AssistantRunKind kind) {
        return new AssistantBatch(anthropicBatchId, kind);
    }

    /** Updates the tracked status from a poll that found the batch not yet ended. */
    public void updateStatus(AssistantBatchStatus status) {
        this.status = status;
    }

    /** Marks the batch {@code ENDED} once every result has been written back onto its runs. */
    public void end() {
        this.status = AssistantBatchStatus.ENDED;
        this.endedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAnthropicBatchId() {
        return anthropicBatchId;
    }

    public AssistantRunKind getKind() {
        return kind;
    }

    public AssistantBatchStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
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
