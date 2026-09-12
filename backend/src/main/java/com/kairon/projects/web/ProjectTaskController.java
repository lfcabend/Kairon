package com.kairon.projects.web;

import java.util.List;
import java.util.UUID;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.ProjectTaskService;
import com.kairon.projects.app.ProjectTaskService.CreateCommand;
import com.kairon.projects.app.ProjectTaskService.PatchCommand;
import com.kairon.projects.web.ProjectTaskDtos.CreateProjectTaskRequest;
import com.kairon.projects.web.ProjectTaskDtos.PatchProjectTaskRequest;
import com.kairon.projects.web.ProjectTaskDtos.ProjectTaskPageResponse;
import com.kairon.projects.web.ProjectTaskDtos.ProjectTaskResponse;
import com.kairon.projects.web.ProjectTaskDtos.ReorderRequest;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * The project task endpoints — {@code GET/POST /projects/{id}/tasks},
 * {@code PATCH/DELETE /tasks/{taskId}}, {@code POST /projects/{id}/tasks:reorder}
 * (D2/D5/D23). The class mapping is {@code /api/v1} (same reasoning as
 * {@code TodoController}/{@code JournalController}) so {@code :reorder}
 * resolves without the path combiner inserting a slash before the colon.
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectTaskController {

    private static final Logger log = LoggerFactory.getLogger(ProjectTaskController.class);

    private final ProjectTaskService tasks;

    public ProjectTaskController(ProjectTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/projects/{id}/tasks")
    public ProjectTaskPageResponse list(
            @CurrentUser UserId userId,
            @PathVariable UUID id,
            @RequestParam(required = false) @Nullable Integer page,
            @RequestParam(required = false) @Nullable Integer size,
            @RequestParam(required = false) @Nullable List<String> sort) {
        log.debug("GET /projects/{}/tasks userId={} page={} size={}", id, userId.value(), page, size);
        return ProjectTaskPageResponse.from(tasks.list(userId, id, page, size, sort));
    }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectTaskResponse create(@CurrentUser UserId userId, @PathVariable UUID id,
            @Valid @RequestBody CreateProjectTaskRequest request) {
        log.debug("POST /projects/{}/tasks userId={} parentTaskId={}", id, userId.value(), request.parentTaskId());
        return ProjectTaskResponse.from(tasks.create(userId, id, new CreateCommand(
                request.name(), request.description(), request.parentTaskId(), request.plannedStart(),
                request.plannedEnd(), request.estimateHours(), request.isMilestoneOrDefault())));
    }

    @PatchMapping("/tasks/{taskId}")
    public ProjectTaskResponse patch(@CurrentUser UserId userId, @PathVariable UUID taskId,
            @Valid @RequestBody PatchProjectTaskRequest request) {
        log.debug("PATCH /tasks/{} userId={}", taskId, userId.value());
        return ProjectTaskResponse.from(tasks.patch(userId, taskId, new PatchCommand(
                request.name(), request.description(), request.status(), request.parentTaskId(),
                request.plannedStart(), request.plannedEnd(), request.estimateHours(), request.actualHours(),
                request.progressPercent(), request.isMilestone(), request.expectedVersion())));
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserId userId, @PathVariable UUID taskId) {
        log.debug("DELETE /tasks/{} userId={}", taskId, userId.value());
        tasks.delete(userId, taskId);
    }

    @PostMapping("/projects/{id}/tasks:reorder")
    public List<ProjectTaskResponse> reorder(@CurrentUser UserId userId, @PathVariable UUID id,
            @Valid @RequestBody ReorderRequest request) {
        log.debug("POST /projects/{}/tasks:reorder userId={} parentTaskId={} count={}",
                id, userId.value(), request.parentTaskId(), request.orderedIds().size());
        return ProjectTaskResponse.from(tasks.reorder(userId, id, request.parentTaskId(), request.orderedIds()));
    }
}
