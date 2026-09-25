package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoApi;
import com.kairon.todo.api.TodoApi.NewTodo;
import com.kairon.todo.api.TodoItemView;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestedTaskServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Mock
    AssistantSuggestedTaskRepository suggestedTasks;

    @Mock
    TodoApi todos;

    SuggestedTaskService service;

    @BeforeEach
    void setUp() {
        service = new SuggestedTaskService(suggestedTasks, todos);
    }

    private static AssistantSuggestedTask proposed() {
        return AssistantSuggestedTask.propose(UUID.randomUUID(), USER.value(), "Order cabinet hardware",
                null, "Overdue kitchen-remodel task", DAY, 20, null, 0);
    }

    @Test
    void acceptCreatesALinkedTodoAndMarksTheSuggestionAccepted() {
        AssistantSuggestedTask task = proposed();
        when(suggestedTasks.findByIdAndUserId(task.getId(), USER.value())).thenReturn(Optional.of(task));
        TodoItemView created = new TodoItemView(UUID.randomUUID(), DAY, "Order cabinet hardware", null,
                "OPEN", 0, 100, null, null, null, null, null, null, 0);
        when(todos.create(eq(USER), any(NewTodo.class))).thenReturn(created);

        TodoItemView result = service.accept(USER, task.getId());

        assertThat(result).isEqualTo(created);
        assertThat(task.getStatus().name()).isEqualTo("ACCEPTED");
        assertThat(task.getAcceptedTodoItemId()).isEqualTo(created.id());
        verify(todos).create(USER, new NewTodo(DAY, "Order cabinet hardware", null, 0, 20, null));
    }

    @Test
    void acceptFallsBackToAnUnlinkedTodoWhenTheSourceProjectTaskIsNoLongerValid() {
        UUID staleTaskId = UUID.randomUUID();
        AssistantSuggestedTask task = AssistantSuggestedTask.propose(UUID.randomUUID(), USER.value(),
                "Order cabinet hardware", null, "Overdue kitchen-remodel task", DAY, 20, staleTaskId, 0);
        when(suggestedTasks.findByIdAndUserId(task.getId(), USER.value())).thenReturn(Optional.of(task));
        TodoItemView created = new TodoItemView(UUID.randomUUID(), DAY, "Order cabinet hardware", null,
                "OPEN", 0, 100, null, null, null, null, null, null, 0);
        when(todos.create(eq(USER), eq(new NewTodo(DAY, "Order cabinet hardware", null, 0, 20, staleTaskId))))
                .thenThrow(ApiException.notFound("Task not found."));
        when(todos.create(eq(USER), eq(new NewTodo(DAY, "Order cabinet hardware", null, 0, 20, null))))
                .thenReturn(created);

        TodoItemView result = service.accept(USER, task.getId());

        assertThat(result).isEqualTo(created);
        assertThat(task.getStatus().name()).isEqualTo("ACCEPTED");
        verify(todos).create(USER, new NewTodo(DAY, "Order cabinet hardware", null, 0, 20, null));
    }

    @Test
    void dismissMarksTheSuggestionDismissedWithoutTouchingTodos() {
        AssistantSuggestedTask task = proposed();
        when(suggestedTasks.findByIdAndUserId(task.getId(), USER.value())).thenReturn(Optional.of(task));

        AssistantSuggestedTaskView view = service.dismiss(USER, task.getId());

        assertThat(view.status()).isEqualTo("DISMISSED");
        verify(todos, never()).create(any(), any());
    }

    @Test
    void acceptOnAnAlreadyResolvedSuggestionIsAConflict() {
        AssistantSuggestedTask task = proposed();
        task.dismiss();
        when(suggestedTasks.findByIdAndUserId(task.getId(), USER.value())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.accept(USER, task.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        verify(todos, never()).create(any(), any());
    }

    @Test
    void missingOrForeignSuggestionIs404() {
        UUID id = UUID.randomUUID();
        when(suggestedTasks.findByIdAndUserId(id, USER.value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(USER, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }
}
