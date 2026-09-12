package com.kairon.projects.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The public projection of a project, used by the web layer's response DTOs.
 * Feature modules depend on this record and the {@link ProjectsApi} port,
 * never on {@code projects.domain} or {@code projects.repo}
 * (ArchUnit-enforced — docs/DESIGN.md §3.1).
 */
public record ProjectView(
        UUID id,
        UUID categoryId,
        String name,
        String description,
        String status,
        String size,
        int priorityRank,
        String color,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate actualStart,
        LocalDate actualEnd,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
