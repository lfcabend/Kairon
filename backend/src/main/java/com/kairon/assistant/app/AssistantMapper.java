package com.kairon.assistant.app;

import java.util.List;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;

/** Hand-rolled entity -> view mapping, matching the rest of the app (e.g. ProjectTaskMapper). */
final class AssistantMapper {

    private AssistantMapper() {
    }

    static AssistantRunView toRunView(AssistantRun run, List<AssistantSuggestedTask> tasks) {
        return toRunView(run, tasks, null, null);
    }

    static AssistantRunView toRunView(AssistantRun run, List<AssistantSuggestedTask> tasks,
            AssistantSuggestedProjectView suggestedProject) {
        return toRunView(run, tasks, suggestedProject, null);
    }

    /**
     * The light row shape for {@code GET /assistant/runs}' history list (M9
     * D12) — omits {@code outputMarkdown}/{@code suggestions}/
     * {@code suggestedProject}/{@code suggestedProjectEdit} bodies; the
     * detail endpoint ({@code GET /assistant/runs/{id}}, {@link #toRunView})
     * still carries them.
     */
    static AssistantRunView toRunListView(AssistantRun run) {
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
                List.of(),
                null,
                null,
                null);
    }

    static AssistantRunView toRunView(AssistantRun run, List<AssistantSuggestedTask> tasks,
            AssistantSuggestedProjectView suggestedProject, AssistantSuggestedProjectEditView suggestedProjectEdit) {
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
                suggestedProject,
                suggestedProjectEdit,
                run.getOutputMarkdown());
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
                plan.categoryId(),
                plan.categoryName(),
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

    static AssistantSuggestedProjectEditView toSuggestedProjectEditView(AssistantSuggestedProjectEdit row,
            PersistedProjectEdit diff) {
        ProjectFieldChangesView projectChanges = diff.projectChanges() == null ? null
                : new ProjectFieldChangesView(diff.projectChanges().name(), diff.projectChanges().description(),
                        diff.projectChanges().size(), diff.projectChanges().startDate(),
                        diff.projectChanges().endDate(), diff.projectChanges().categoryId(),
                        diff.projectChanges().categoryName());
        return new AssistantSuggestedProjectEditView(
                row.getId(),
                row.getRunId(),
                row.getStatus().name(),
                row.getProjectId(),
                projectChanges,
                diff.taskOperations().stream()
                        .map(t -> new TaskOperationView(t.op(), parseUuidOrNull(t.existingTaskId()), t.key(),
                                t.parentRef(), t.name(), t.description(), t.isMilestone(), t.plannedStart(),
                                t.plannedEnd(), t.estimateHours()))
                        .toList(),
                diff.dependencyOperations().stream()
                        .map(d -> new DependencyOperationView(d.op(), parseUuidOrNull(d.existingDependencyId()),
                                d.predecessorRef(), d.successorRef(), d.type(), d.lagDays()))
                        .toList(),
                diff.reorderOperations().stream()
                        .map(r -> new ReorderOperationView(r.parentRef(), r.orderedRefs()))
                        .toList());
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
