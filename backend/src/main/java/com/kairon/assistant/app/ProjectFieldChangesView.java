package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.UUID;

/** Project-level field changes proposed by a {@code PROJECT_EDIT} run, if any. */
public record ProjectFieldChangesView(
        String name,
        String description,
        String size,
        LocalDate startDate,
        LocalDate endDate,
        UUID categoryId,
        String categoryName) {
}
