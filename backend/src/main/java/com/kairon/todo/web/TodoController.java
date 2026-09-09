package com.kairon.todo.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.todo.app.RolloverService;
import com.kairon.todo.app.RolloverService.RolloverCommand;
import com.kairon.todo.app.RolloverService.UndoCommand;
import com.kairon.todo.app.TodoService;
import com.kairon.todo.app.TodoService.CreateCommand;
import com.kairon.todo.app.TodoService.PatchCommand;
import com.kairon.todo.web.TodoDtos.CompleteRequest;
import com.kairon.todo.web.TodoDtos.CreateTodoRequest;
import com.kairon.todo.web.TodoDtos.PatchTodoRequest;
import com.kairon.todo.web.TodoDtos.ReorderRequest;
import com.kairon.todo.web.TodoDtos.RolloverPreviewResponse;
import com.kairon.todo.web.TodoDtos.RolloverRequest;
import com.kairon.todo.web.TodoDtos.RolloverResponse;
import com.kairon.todo.web.TodoDtos.RolloverUndoRequest;
import com.kairon.todo.web.TodoDtos.RolloverUndoResponse;
import com.kairon.todo.web.TodoDtos.TodoItemResponse;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/todo} — the day view's backend. Mirrors {@code MeController}:
 * constructor injection, {@code @CurrentUser UserId}, {@code @Valid} bodies, and
 * DTOs only. List endpoints return a bare array (a day's list is a small, fully
 * ordered set with no pagination need — docs/milestones/M2 §5).
 *
 * <p>The class mapping is {@code /api/v1} rather than {@code /api/v1/todo} so the
 * AIP-style custom methods ({@code /todo:reorder}, {@code /todo:rollover}) resolve
 * — Spring's path combiner would otherwise insert a slash before the colon.
 */
@RestController
@RequestMapping("/api/v1")
public class TodoController {

    private final TodoService todos;
    private final RolloverService rollovers;

    public TodoController(TodoService todos, RolloverService rollovers) {
        this.todos = todos;
        this.rollovers = rollovers;
    }

    @GetMapping("/todo")
    public List<TodoItemResponse> list(
            @CurrentUser UserId userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate day,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        if (day != null) {
            return TodoItemResponse.from(todos.list(userId, day));
        }
        if (from != null && to != null) {
            return TodoItemResponse.from(todos.range(userId, from, to));
        }
        throw ApiException.badRequest("Provide either `day` or both `from` and `to`.");
    }

    @PostMapping("/todo")
    @ResponseStatus(HttpStatus.CREATED)
    public TodoItemResponse create(@CurrentUser UserId userId, @Valid @RequestBody CreateTodoRequest request) {
        return TodoItemResponse.from(todos.create(userId, new CreateCommand(
                request.day(), request.title(), request.notes(), request.priority(),
                request.estimateMinutes(), request.sourceProjectTaskId())));
    }

    @PatchMapping("/todo/{id}")
    public TodoItemResponse patch(@CurrentUser UserId userId, @PathVariable UUID id,
            @Valid @RequestBody PatchTodoRequest request) {
        return TodoItemResponse.from(todos.patch(userId, id, new PatchCommand(
                request.title(), request.notes(), request.priority(), request.estimateMinutes(),
                request.status(), request.expectedVersion())));
    }

    @DeleteMapping("/todo/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserId userId, @PathVariable UUID id) {
        todos.softDelete(userId, id);
    }

    @PostMapping("/todo/{id}:complete")
    public TodoItemResponse complete(@CurrentUser UserId userId, @PathVariable UUID id,
            @RequestBody(required = false) @Nullable CompleteRequest request) {
        boolean complete = request == null || request.orDefault();
        return TodoItemResponse.from(todos.complete(userId, id, complete));
    }

    @PostMapping("/todo:reorder")
    public List<TodoItemResponse> reorder(@CurrentUser UserId userId,
            @Valid @RequestBody ReorderRequest request) {
        return TodoItemResponse.from(todos.reorder(userId, request.day(), request.orderedIds()));
    }

    @GetMapping("/todo/rollover-preview")
    public RolloverPreviewResponse rolloverPreview(@CurrentUser UserId userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDay) {
        return RolloverPreviewResponse.from(rollovers.preview(userId, onDay));
    }

    @PostMapping("/todo:rollover")
    public RolloverResponse rollover(@CurrentUser UserId userId,
            @Valid @RequestBody RolloverRequest request) {
        return RolloverResponse.from(rollovers.rollover(userId,
                new RolloverCommand(request.toDay(), request.fromDay(), request.ids())));
    }

    @PostMapping("/todo:rollover-undo")
    public RolloverUndoResponse rolloverUndo(@CurrentUser UserId userId,
            @Valid @RequestBody RolloverUndoRequest request) {
        return RolloverUndoResponse.from(rollovers.undo(userId, new UndoCommand(request.createdIds())));
    }
}
