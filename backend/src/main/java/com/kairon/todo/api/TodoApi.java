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
     * Creates a plain {@code OPEN} item for the user, appended to {@code day}'s
     * list. {@code sourceProjectTaskId} links it back to the promoted task.
     */
    TodoItemView create(UserId userId, NewTodo command);

    /** Minimal create payload for cross-module callers. */
    record NewTodo(
            LocalDate day,
            String title,
            String notes,
            Integer priority,
            Integer estimateMinutes,
            UUID sourceProjectTaskId) {
    }
}
