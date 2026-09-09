package com.kairon.todo.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.app.RolloverService.RolloverPreview;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for {@code /api/v1/todo}. Controllers never see the
 * entity (ArchUnit-enforced); {@link TodoItemResponse#from} is the one mapping
 * point from the {@code api} view to the wire shape.
 */
final class TodoDtos {

    private TodoDtos() {
    }

    record CreateTodoRequest(
            @NotNull LocalDate day,
            @NotBlank @Size(max = 500) String title,
            String notes,
            @Min(0) @Max(3) Integer priority,
            @Min(1) Integer estimateMinutes,
            UUID sourceProjectTaskId) {
    }

    /** All fields optional; a null field leaves that attribute unchanged. */
    record PatchTodoRequest(
            @Size(max = 500) String title,
            String notes,
            @Min(0) @Max(3) Integer priority,
            @Min(0) Integer estimateMinutes,
            String status,
            Long expectedVersion) {
    }

    record CompleteRequest(Boolean complete) {
        boolean orDefault() {
            return complete == null || complete;
        }
    }

    record ReorderRequest(
            @NotNull LocalDate day,
            @NotEmpty List<UUID> orderedIds) {
    }

    record RolloverRequest(
            @NotNull LocalDate toDay,
            LocalDate fromDay,
            List<UUID> ids) {
    }

    record RolloverUndoRequest(
            @NotEmpty List<UUID> createdIds) {
    }

    record TodoItemResponse(
            UUID id,
            LocalDate day,
            String title,
            String notes,
            String status,
            int priority,
            int position,
            Integer estimateMinutes,
            UUID sourceProjectTaskId,
            UUID rolledOverFromId,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static TodoItemResponse from(TodoItemView v) {
            return new TodoItemResponse(v.id(), v.day(), v.title(), v.notes(), v.status(),
                    v.priority(), v.position(), v.estimateMinutes(), v.sourceProjectTaskId(),
                    v.rolledOverFromId(), v.completedAt(), v.createdAt(), v.updatedAt(), v.version());
        }

        static List<TodoItemResponse> from(List<TodoItemView> views) {
            return views.stream().map(TodoItemResponse::from).toList();
        }
    }

    record SourceDayResponse(LocalDate day, List<TodoItemResponse> items) {
    }

    record RolloverPreviewResponse(List<SourceDayResponse> sourceDays, int totalItems) {

        static RolloverPreviewResponse from(RolloverPreview preview) {
            List<SourceDayResponse> days = preview.sourceDays().stream()
                    .map(sd -> new SourceDayResponse(sd.day(), TodoItemResponse.from(sd.items())))
                    .toList();
            return new RolloverPreviewResponse(days, preview.totalItems());
        }
    }

    record RolloverResponse(List<TodoItemResponse> rolledOver) {

        static RolloverResponse from(List<TodoItemView> views) {
            return new RolloverResponse(TodoItemResponse.from(views));
        }
    }

    record RolloverUndoResponse(List<TodoItemResponse> reopened) {

        static RolloverUndoResponse from(List<TodoItemView> views) {
            return new RolloverUndoResponse(TodoItemResponse.from(views));
        }
    }
}
