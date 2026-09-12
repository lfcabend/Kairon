package com.kairon.projects.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The public projection of a project task. Carries the owning project's
 * {@code name}/{@code color} denormalized (Q2) so a cross-module consumer
 * (M6's Today screen) doesn't need a second lookup per task.
 */
public record ProjectTaskView(
        UUID id,
        UUID projectId,
        String projectName,
        String projectColor,
        UUID parentTaskId,
        String name,
        String description,
        String status,
        boolean isMilestone,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        BigDecimal estimateHours,
        BigDecimal actualHours,
        int progressPercent,
        int position,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
