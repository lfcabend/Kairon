package com.kairon.projects.app;

import java.time.Instant;
import java.util.UUID;

/**
 * The internal projection of a {@code task_dependency} edge, including the
 * computed {@code violatesConstraint} flag (D13). Stays in {@code app}, not
 * {@code api} — no other module needs dependency data in M5 (D7's package
 * layout note).
 */
public record TaskDependencyView(
        UUID id,
        UUID predecessorId,
        UUID successorId,
        String type,
        int lagDays,
        boolean violatesConstraint,
        Instant createdAt) {
}
