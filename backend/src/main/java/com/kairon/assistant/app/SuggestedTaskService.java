package com.kairon.assistant.app;

import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoApi;
import com.kairon.todo.api.TodoItemView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accept/dismiss for one {@code assistant_suggested_task}. Accept creates a
 * real {@code todo_item} via {@link TodoApi#create} — the same shape
 * {@code PlanningService.promote} already established (M6) — never writes one
 * itself outside an explicit user action (docs/adr/0002, M8 D13).
 */
@Service
public class SuggestedTaskService {

    private static final Logger log = LoggerFactory.getLogger(SuggestedTaskService.class);

    private final AssistantSuggestedTaskRepository suggestedTasks;
    private final TodoApi todos;

    public SuggestedTaskService(AssistantSuggestedTaskRepository suggestedTasks, TodoApi todos) {
        this.suggestedTasks = suggestedTasks;
        this.todos = todos;
    }

    @Transactional
    public TodoItemView accept(UserId userId, UUID id) {
        AssistantSuggestedTask task = require(userId, id);
        requireProposed(task);
        TodoItemView created = createLinkedTodo(userId, task, task.getSourceProjectTaskId());
        task.accept(created.id());
        suggestedTasks.save(task);
        log.info("Accepted suggestion {} -> todo {} userId={}", id, created.id(), userId.value());
        return created;
    }

    /**
     * {@code sourceProjectTaskId} is the model's own output (never validated
     * when the suggestion was proposed — {@code AssistantRunService}), so by
     * the time the user accepts it, the task it names may be stale, deleted,
     * or hallucinated. {@code TodoApi#create} now 404s on an unresolvable
     * link; rather than let that block an otherwise-good suggestion, drop the
     * link and create a plain todo instead.
     */
    private TodoItemView createLinkedTodo(UserId userId, AssistantSuggestedTask task, UUID sourceProjectTaskId) {
        try {
            return todos.create(userId, new TodoApi.NewTodo(
                    task.getSuggestedForDay(), task.getTitle(), task.getNotes(), 0,
                    task.getEstimateMinutes(), sourceProjectTaskId));
        } catch (ApiException ex) {
            if (sourceProjectTaskId == null) {
                throw ex;
            }
            log.warn("Suggestion {} referenced project task {} which is no longer valid;"
                            + " accepting without the link. userId={}",
                    task.getId(), sourceProjectTaskId, userId.value());
            return createLinkedTodo(userId, task, null);
        }
    }

    @Transactional
    public AssistantSuggestedTaskView dismiss(UserId userId, UUID id) {
        AssistantSuggestedTask task = require(userId, id);
        requireProposed(task);
        task.dismiss();
        suggestedTasks.save(task);
        log.info("Dismissed suggestion {} userId={}", id, userId.value());
        return AssistantMapper.toSuggestedTaskView(task);
    }

    private void requireProposed(AssistantSuggestedTask task) {
        if (task.isResolved()) {
            throw ApiException.conflict(
                    "This suggestion was already " + task.getStatus().name().toLowerCase() + ".");
        }
    }

    private AssistantSuggestedTask require(UserId userId, UUID id) {
        return suggestedTasks.findByIdAndUserId(id, userId.value())
                .orElseThrow(() -> ApiException.notFound("Suggested task not found."));
    }
}
