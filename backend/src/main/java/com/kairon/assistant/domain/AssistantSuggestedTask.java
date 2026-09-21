package com.kairon.assistant.domain;

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
 * One proposed todo item from a {@code TODO_SUGGESTION} run
 * ({@code PROPOSED -> ACCEPTED | DISMISSED}). Accepting creates a real
 * {@code todo_item} (the assistant never writes one itself — always an
 * explicit user action, docs/adr/0002).
 */
@Entity
@Table(name = "assistant_suggested_task")
public class AssistantSuggestedTask {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column
    private String notes;

    @Column
    private String rationale;

    @Column(name = "suggested_for_day")
    private LocalDate suggestedForDay;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    @Column(name = "source_project_task_id")
    private UUID sourceProjectTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantSuggestedTaskStatus status;

    @Column(name = "accepted_todo_item_id")
    private UUID acceptedTodoItemId;

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

    protected AssistantSuggestedTask() {
    }

    private AssistantSuggestedTask(UUID runId, UUID userId, String title, String notes, String rationale,
            LocalDate suggestedForDay, Integer estimateMinutes, UUID sourceProjectTaskId, int position) {
        this.id = Uuidv7.next();
        this.runId = runId;
        this.userId = userId;
        this.title = title;
        this.notes = notes;
        this.rationale = rationale;
        this.suggestedForDay = suggestedForDay;
        this.estimateMinutes = estimateMinutes;
        this.sourceProjectTaskId = sourceProjectTaskId;
        this.status = AssistantSuggestedTaskStatus.PROPOSED;
        this.position = position;
    }

    /** A fresh suggestion, {@code PROPOSED}, from a run's structured-output payload. */
    public static AssistantSuggestedTask propose(UUID runId, UUID userId, String title, String notes,
            String rationale, LocalDate suggestedForDay, Integer estimateMinutes, UUID sourceProjectTaskId,
            int position) {
        return new AssistantSuggestedTask(runId, userId, title, notes, rationale, suggestedForDay,
                estimateMinutes, sourceProjectTaskId, position);
    }

    public void accept(UUID acceptedTodoItemId) {
        this.status = AssistantSuggestedTaskStatus.ACCEPTED;
        this.acceptedTodoItemId = acceptedTodoItemId;
    }

    public void dismiss() {
        this.status = AssistantSuggestedTaskStatus.DISMISSED;
    }

    public boolean isResolved() {
        return status != AssistantSuggestedTaskStatus.PROPOSED;
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

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public String getRationale() {
        return rationale;
    }

    public LocalDate getSuggestedForDay() {
        return suggestedForDay;
    }

    public Integer getEstimateMinutes() {
        return estimateMinutes;
    }

    public UUID getSourceProjectTaskId() {
        return sourceProjectTaskId;
    }

    public AssistantSuggestedTaskStatus getStatus() {
        return status;
    }

    public UUID getAcceptedTodoItemId() {
        return acceptedTodoItemId;
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
