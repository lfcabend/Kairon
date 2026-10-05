package com.kairon.assistant.domain;

/**
 * Mirrors {@code com.anthropic.models.messages.batches.MessageBatch.ProcessingStatus.Known}
 * 1:1 (docs/milestones/M10-journal-reflection.md D18/D21) — a separate enum so
 * {@code assistant.domain} never leaks the Anthropic SDK type into JPA.
 */
public enum AssistantBatchStatus {
    IN_PROGRESS,
    CANCELING,
    ENDED
}
