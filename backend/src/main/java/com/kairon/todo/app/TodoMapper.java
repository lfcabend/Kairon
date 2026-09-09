package com.kairon.todo.app;

import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.domain.TodoItem;

/**
 * Hand-rolled entity → view mapping, following the {@code identity} precedent
 * (static factory, no MapStruct — docs/milestones/M2 D5).
 */
final class TodoMapper {

    private TodoMapper() {
    }

    static TodoItemView toView(TodoItem item) {
        return new TodoItemView(
                item.getId(),
                item.getDay(),
                item.getTitle(),
                item.getNotes(),
                item.getStatus().name(),
                item.getPriority(),
                item.getPosition(),
                item.getEstimateMinutes(),
                item.getSourceProjectTaskId(),
                item.getRolledOverFromId(),
                item.getCompletedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion());
    }
}
