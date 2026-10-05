package com.kairon.assistant.llm;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** See {@link ProjectEditPayload#projectChanges()}. */
public record ProjectFieldChangesPayload(
        String name, String description, String size, LocalDate startDate, LocalDate endDate,
        @JsonPropertyDescription("Match one of the user's existing categories (listed in the prompt) "
                + "exactly by name if one clearly fits; otherwise a short new category name; echo the "
                + "project's current category name if it isn't changing.")
        String categoryName) {
}
