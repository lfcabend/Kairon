package com.kairon.todo.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.security.UserId;

/**
 * The todo module's public port. Other modules depend only on this interface and
 * the DTOs in this package, never on {@code todo.domain} or {@code todo.repo}
 * (ArchUnit-enforced — docs/DESIGN.md §3.1). Implemented by
 * {@code com.kairon.todo.app.TodoService}.
 *
 * <p>Kept deliberately minimal: M6 {@code planning} needs the day's items and a
 * way to create one when the user promotes a project task into today's list.
 */
public interface TodoApi {

    /** The user's non-deleted items for {@code day}, in list order. */
    List<TodoItemView> forDay(UserId userId, LocalDate day);

    /**
     * The user's non-deleted items in {@code [from, to]}, ordered by day then
     * position. Added for M8's assistant module (todo-suggestion context: recent
     * history as a capacity signal — docs/milestones/M8-assistant-foundations.md D3).
     */
    List<TodoItemView> range(UserId userId, LocalDate from, LocalDate to);

    /**
     * Creates a plain {@code OPEN} item for the user, appended to {@code day}'s
     * list. {@code sourceProjectTaskId} links it back to the promoted task.
     */
    TodoItemView create(UserId userId, NewTodo command);

    /**
     * Created/completed/rolled-over counts for the user's items with {@code day}
     * in {@code [from, to]}. Added for M9's execution-summary aggregation
     * (docs/milestones/M9-execution-summaries.md D4).
     */
    PeriodStats periodStats(UserId userId, LocalDate from, LocalDate to);

    /** Minimal create payload for cross-module callers. */
    record NewTodo(
            LocalDate day,
            String title,
            String notes,
            Integer priority,
            Integer estimateMinutes,
            UUID sourceProjectTaskId) {
    }

    record PeriodStats(long created, long completed, long rolledOver) {
        public double completionRate() {
            long onDocket = created + rolledOver;
            return onDocket == 0 ? 0.0 : (double) completed / onDocket;
        }
    }
}
