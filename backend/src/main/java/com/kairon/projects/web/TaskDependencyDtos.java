package com.kairon.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.projects.app.TaskDependencyView;

import jakarta.validation.constraints.NotNull;

/**
 * Request/response bodies for the dependency endpoints. Same conventions as
 * {@link ProjectTaskDtos} — controllers never see the entity or the app-layer
 * view directly in the response, only through {@code TaskDependencyResponse.from}.
 */
final class TaskDependencyDtos {

    private TaskDependencyDtos() {
    }

    /** {@code type}/{@code lagDays} default {@code FS}/{@code 0} in the service when omitted (D2). */
    record CreateTaskDependencyRequest(@NotNull UUID predecessorId, String type, Integer lagDays) {
    }

    record TaskDependencyResponse(
            UUID id,
            UUID predecessorId,
            UUID successorId,
            String type,
            int lagDays,
            boolean violatesConstraint,
            Instant createdAt) {

        static TaskDependencyResponse from(TaskDependencyView v) {
            return new TaskDependencyResponse(v.id(), v.predecessorId(), v.successorId(), v.type(), v.lagDays(),
                    v.violatesConstraint(), v.createdAt());
        }

        static List<TaskDependencyResponse> from(List<TaskDependencyView> views) {
            return views.stream().map(TaskDependencyResponse::from).toList();
        }
    }
}
