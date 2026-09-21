package com.kairon.assistant.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * The audit/lifecycle record for one call to the Anthropic API:
 * {@code PENDING -> RUNNING -> SUCCEEDED | FAILED}. {@link #inputSnapshot} is
 * exactly what was gathered and sent, so a user can audit what left the
 * instance (docs/DESIGN.md §13.3/§13.5). No soft delete — deleting a run
 * purges its snapshot.
 */
@Entity
@Table(name = "assistant_run")
public class AssistantRun {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AssistantRunKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssistantRunStatus status;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(nullable = false, length = 60)
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false)
    private Map<String, Object> inputSnapshot = new HashMap<>();

    @Column(name = "output_markdown")
    private String outputMarkdown;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AssistantRun() {
    }

    private AssistantRun(UUID userId, AssistantRunKind kind, LocalDate periodStart, LocalDate periodEnd,
            String model) {
        this.id = Uuidv7.next();
        this.userId = userId;
        this.kind = kind;
        this.status = AssistantRunStatus.PENDING;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.model = model;
    }

    /** A fresh run, {@code PENDING}, not yet given its context or called out. */
    public static AssistantRun pending(UUID userId, AssistantRunKind kind, LocalDate periodStart,
            LocalDate periodEnd, String model) {
        return new AssistantRun(userId, kind, periodStart, periodEnd, model);
    }

    /** Moves to {@code RUNNING} once the context sent to the model is known. */
    public void start(Map<String, Object> inputSnapshot) {
        this.status = AssistantRunStatus.RUNNING;
        this.inputSnapshot = inputSnapshot == null ? new HashMap<>() : new HashMap<>(inputSnapshot);
    }

    /** Moves to {@code SUCCEEDED} with the token counts from the API response. */
    public void succeed(long inputTokens, long outputTokens) {
        this.status = AssistantRunStatus.SUCCEEDED;
        this.inputTokens = (int) inputTokens;
        this.outputTokens = (int) outputTokens;
    }

    /** Moves to {@code FAILED} with a short, sanitized detail (never a raw exception message). */
    public void fail(String error) {
        this.status = AssistantRunStatus.FAILED;
        this.error = error;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AssistantRunKind getKind() {
        return kind;
    }

    public AssistantRunStatus getStatus() {
        return status;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getModel() {
        return model;
    }

    public Map<String, Object> getInputSnapshot() {
        return inputSnapshot;
    }

    public String getOutputMarkdown() {
        return outputMarkdown;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public String getError() {
        return error;
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
