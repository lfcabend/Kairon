package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.llm.PlannedDependencyPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;

/**
 * What's actually persisted as {@code assistant_suggested_project.plan} —
 * the model's {@code ProjectPlanPayload} (name/description/size/tasks/
 * dependencies) plus the caller-supplied {@code startDate}/{@code endDate}
 * the model itself never chooses (docs/milestones/M8.5-project-generation.md
 * §3/D4 — an explicit start date anchors every relative date the model
 * proposes), plus the category decision resolved against the user's
 * existing categories right after the model call ({@link AssistantRunService}):
 * a non-null {@code categoryId} means the model's {@code categoryName}
 * exactly matched an existing category (whose name is carried in
 * {@code categoryName} too, for display); a null {@code categoryId} with a
 * non-null {@code categoryName} means no existing category matched and this
 * is the name of a new one to create on accept; both null means
 * uncategorized.
 */
record PersistedProjectPlan(
        String name, String description, String size, LocalDate startDate, LocalDate endDate,
        UUID categoryId, String categoryName,
        List<PlannedTaskPayload> tasks, List<PlannedDependencyPayload> dependencies) {
}
