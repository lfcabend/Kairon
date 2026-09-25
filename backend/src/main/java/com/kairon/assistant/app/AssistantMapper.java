package com.kairon.assistant.app;

import java.util.List;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedTask;

/** Hand-rolled entity -> view mapping, matching the rest of the app (e.g. ProjectTaskMapper). */
final class AssistantMapper {

    private AssistantMapper() {
    }

    static AssistantRunView toRunView(AssistantRun run, List<AssistantSuggestedTask> tasks) {
        return toRunView(run, tasks, null);
    }

    static AssistantRunView toRunView(AssistantRun run, List<AssistantSuggestedTask> tasks,
            AssistantSuggestedProjectView suggestedProject) {
        return new AssistantRunView(
                run.getId(),
                run.getKind().name(),
                run.getStatus().name(),
                run.getModel(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getInputTokens(),
                run.getOutputTokens(),
                run.getError(),
                run.getCreatedAt(),
                tasks.stream().map(AssistantMapper::toSuggestedTaskView).toList(),
                suggestedProject);
    }

    static AssistantSuggestedProjectView toSuggestedProjectView(AssistantSuggestedProject row,
            PersistedProjectPlan plan) {
        return new AssistantSuggestedProjectView(
                row.getId(),
                row.getRunId(),
                row.getStatus().name(),
                plan.name(),
                plan.description(),
                plan.size(),
                plan.startDate(),
                plan.endDate(),
                plan.tasks().stream()
                        .map(t -> new PlannedTaskView(t.key(), t.parentKey(), t.name(), t.description(),
                                t.isMilestone(), t.plannedStart(), t.plannedEnd(), t.estimateHours()))
                        .toList(),
                plan.dependencies().stream()
                        .map(d -> new PlannedDependencyView(d.predecessorKey(), d.successorKey(), d.type(),
                                d.lagDays()))
                        .toList(),
                row.getAcceptedProjectId());
    }

    static AssistantSuggestedTaskView toSuggestedTaskView(AssistantSuggestedTask task) {
        return new AssistantSuggestedTaskView(
                task.getId(),
                task.getRunId(),
                task.getTitle(),
                task.getNotes(),
                task.getRationale(),
                task.getSuggestedForDay(),
                task.getEstimateMinutes(),
                task.getSourceProjectTaskId(),
                task.getStatus().name(),
                task.getAcceptedTodoItemId(),
                task.getPosition());
    }
}
