package com.kairon.projects.web;

import java.util.List;
import java.util.UUID;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.TaskDependencyService;
import com.kairon.projects.app.TaskDependencyService.CreateCommand;
import com.kairon.projects.web.TaskDependencyDtos.CreateTaskDependencyRequest;
import com.kairon.projects.web.TaskDependencyDtos.TaskDependencyResponse;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dependency endpoints (D7): {@code GET /projects/{id}/dependencies},
 * {@code POST /tasks/{taskId}/dependencies}, {@code DELETE /dependencies/{depId}}.
 * Mapping is {@code /api/v1}, same reasoning as {@code ProjectTaskController}.
 */
@RestController
@RequestMapping("/api/v1")
public class TaskDependencyController {

    private static final Logger log = LoggerFactory.getLogger(TaskDependencyController.class);

    private final TaskDependencyService dependencies;

    public TaskDependencyController(TaskDependencyService dependencies) {
        this.dependencies = dependencies;
    }

    @GetMapping("/projects/{id}/dependencies")
    public List<TaskDependencyResponse> list(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("GET /projects/{}/dependencies userId={}", id, userId.value());
        return TaskDependencyResponse.from(dependencies.list(userId, id));
    }

    @PostMapping("/tasks/{taskId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDependencyResponse create(@CurrentUser UserId userId, @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskDependencyRequest request) {
        log.debug("POST /tasks/{}/dependencies userId={} predecessorId={}",
                taskId, userId.value(), request.predecessorId());
        return TaskDependencyResponse.from(dependencies.create(userId, taskId,
                new CreateCommand(request.predecessorId(), request.type(), request.lagDays())));
    }

    @DeleteMapping("/dependencies/{depId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserId userId, @PathVariable UUID depId) {
        log.debug("DELETE /dependencies/{} userId={}", depId, userId.value());
        dependencies.delete(userId, depId);
    }
}
