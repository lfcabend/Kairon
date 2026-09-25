package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;

import com.kairon.assistant.llm.PlannedDependencyPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;

/**
 * What's actually persisted as {@code assistant_suggested_project.plan} —
 * the model's {@code ProjectPlanPayload} (name/description/size/tasks/
 * dependencies) plus the caller-supplied {@code startDate}/{@code endDate}
 * the model itself never chooses (docs/milestones/M8.5-project-generation.md
 * §3/D4 — an explicit start date anchors every relative date the model
 * proposes).
 */
record PersistedProjectPlan(
        String name, String description, String size, LocalDate startDate, LocalDate endDate,
        List<PlannedTaskPayload> tasks, List<PlannedDependencyPayload> dependencies) {
}
