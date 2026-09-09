package com.kairon.todo.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The public projection of a todo item for other modules (M6 {@code planning})
 * and the web layer's response DTO. Feature modules depend on this record and the
 * {@link TodoApi} port, never on {@code todo.domain} or {@code todo.repo}
 * (ArchUnit-enforced — docs/DESIGN.md §3.1).
 */
public record TodoItemView(
        UUID id,
        LocalDate day,
        String title,
        String notes,
        String status,
        int priority,
        int position,
        Integer estimateMinutes,
        UUID sourceProjectTaskId,
        UUID rolledOverFromId,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
