package com.kairon.planning.app;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalApi;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.todo.api.TodoApi;
import com.kairon.todo.api.TodoApi.NewTodo;
import com.kairon.todo.api.TodoItemView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code planning} module's application service: a pure aggregation over
 * {@code todo}, {@code projects}, and {@code journal} for the Today screen
 * (docs/milestones/M6-today.md D1). No {@code domain}/{@code repo} of its own —
 * everything here reads or writes through the other modules' public
 * {@code api} ports.
 */
@Service
@Transactional(readOnly = true)
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final TodoApi todos;
    private final ProjectsApi projects;
    private final JournalApi journal;

    public PlanningService(TodoApi todos, ProjectsApi projects, JournalApi journal) {
        this.todos = todos;
        this.projects = projects;
        this.journal = journal;
    }

    public record TodayView(
            LocalDate date,
            List<TodoItemView> todos,
            List<ProjectTaskView> dueProjectTasks,
            boolean hasJournalEntry) {
    }

    public TodayView today(UserId userId, LocalDate date) {
        List<TodoItemView> todosForDay = todos.forDay(userId, date);
        List<ProjectTaskView> due = projects.dueOrOverdue(userId, date);
        boolean hasEntry = journal.hasEntryForDay(userId, date);
        log.debug("Assembled today view userId={} date={} todos={} dueTasks={} hasEntry={}",
                userId.value(), date, todosForDay.size(), due.size(), hasEntry);
        return new TodayView(date, todosForDay, due, hasEntry);
    }

    @Transactional
    public TodoItemView promote(UserId userId, UUID projectTaskId, LocalDate day) {
        ProjectTaskView task = projects.requireTask(userId, projectTaskId);
        TodoItemView created = todos.create(userId, new NewTodo(
                day, task.name(), null, 0, null, task.id()));
        log.info("Promoted task {} to todo {} userId={} day={}",
                task.id(), created.id(), userId.value(), day);
        return created;
    }
}
