package com.kairon.assistant.app;

import java.util.List;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantSuggestedTask;

/** Hand-rolled entity -> view mapping, matching the rest of the app (e.g. ProjectTaskMapper). */
final class AssistantMapper {

    private AssistantMapper() {
    }

    static AssistantRunView toRunView(AssistantRun run, List<AssistantSuggestedTask> tasks) {
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
                tasks.stream().map(AssistantMapper::toSuggestedTaskView).toList());
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
