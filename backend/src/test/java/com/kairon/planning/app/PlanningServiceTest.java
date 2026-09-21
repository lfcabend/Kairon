package com.kairon.planning.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalApi;
import com.kairon.planning.app.PlanningService.TodayView;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectsApi;
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
class PlanningServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Mock
    TodoApi todos;

    @Mock
    ProjectsApi projects;

    @Mock
    JournalApi journal;

    PlanningService service;

    @BeforeEach
    void setUp() {
        service = new PlanningService(todos, projects, journal);
    }

    private static TodoItemView todo(UUID id) {
        return new TodoItemView(id, DAY, "A todo", null, "OPEN", 0, 100, null, null, null, null,
                Instant.parse("2026-09-18T08:00:00Z"), Instant.parse("2026-09-18T08:00:00Z"), 0);
    }

    private static ProjectTaskView task(UUID id, String name) {
        return new ProjectTaskView(id, UUID.randomUUID(), "Project", "#6366f1", null, name, null,
                "TODO", false, null, null, null, null, 0, 100,
                Instant.parse("2026-09-18T08:00:00Z"), Instant.parse("2026-09-18T08:00:00Z"), 0);
    }

    @Test
    void todayComposesAllThreePortsIntoTheView() {
        TodoItemView todoItem = todo(UUID.randomUUID());
        ProjectTaskView dueTask = task(UUID.randomUUID(), "Order cabinets");
        when(todos.forDay(USER, DAY)).thenReturn(List.of(todoItem));
        when(projects.dueOrOverdue(USER, DAY)).thenReturn(List.of(dueTask));
        when(journal.hasEntryForDay(USER, DAY)).thenReturn(true);

        TodayView view = service.today(USER, DAY);

        assertThat(view.date()).isEqualTo(DAY);
        assertThat(view.todos()).containsExactly(todoItem);
        assertThat(view.dueProjectTasks()).containsExactly(dueTask);
        assertThat(view.hasJournalEntry()).isTrue();
    }

    @Test
    void promoteResolvesTheTaskThenCreatesALinkedTodo() {
        UUID taskId = UUID.randomUUID();
        ProjectTaskView taskView = task(taskId, "Order cabinets");
        TodoItemView created = todo(UUID.randomUUID());
        when(projects.requireTask(USER, taskId)).thenReturn(taskView);
        when(todos.create(eq(USER), any(NewTodo.class))).thenReturn(created);

        TodoItemView result = service.promote(USER, taskId, DAY);

        assertThat(result).isEqualTo(created);
        verify(todos).create(USER, new NewTodo(DAY, "Order cabinets", null, 0, null, taskId));
    }

    @Test
    void promoteWithAForeignOrMissingTaskPropagatesThe404WithoutCreatingATodo() {
        UUID taskId = UUID.randomUUID();
        when(projects.requireTask(USER, taskId)).thenThrow(ApiException.notFound("Task not found."));

        assertThatThrownBy(() -> service.promote(USER, taskId, DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
        verify(todos, never()).create(any(), any());
    }
}
