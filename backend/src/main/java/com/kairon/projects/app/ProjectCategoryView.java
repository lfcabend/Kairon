package com.kairon.projects.app;

import java.time.Instant;
import java.util.UUID;

/**
 * The category's view for the web layer's response DTO. Not part of a
 * cross-module port (no other module needs categories), so it lives in
 * {@code app} rather than {@code api} — but the web layer still only ever
 * sees this, never the {@code ProjectCategory} entity.
 */
public record ProjectCategoryView(
        UUID id,
        String name,
        String color,
        int position,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
