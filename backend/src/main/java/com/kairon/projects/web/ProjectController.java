package com.kairon.projects.web;

import java.util.List;
import java.util.UUID;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.ProjectService;
import com.kairon.projects.app.ProjectService.CreateCommand;
import com.kairon.projects.app.ProjectService.PatchCommand;
import com.kairon.projects.web.ProjectDtos.CreateProjectRequest;
import com.kairon.projects.web.ProjectDtos.PatchProjectRequest;
import com.kairon.projects.web.ProjectDtos.ProjectPageResponse;
import com.kairon.projects.web.ProjectDtos.ProjectResponse;
import com.kairon.projects.web.ProjectDtos.ReorderRequest;

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
 * {@code /api/v1/projects} — project CRUD, the filtered/paginated list, the
 * flattened priority view, and {@code :reorder} (D18-D20). The class mapping
 * is {@code /api/v1} rather than {@code /api/v1/projects} so
 * {@code /projects:reorder} resolves — Spring's path combiner would otherwise
 * insert a slash before the colon.
 *
 * <p>{@link #list} takes {@code page}/{@code pageSize} as plain
 * {@code @RequestParam}s and resolves {@code sort} separately (D17) — the
 * page-size param is renamed because {@code size} is taken by the t-shirt-size
 * filter on this one endpoint.
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping("/projects")
    public ProjectPageResponse list(
            @CurrentUser UserId userId,
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(required = false) @Nullable UUID categoryId,
            @RequestParam(required = false) @Nullable String size,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) @Nullable List<String> sort) {
        log.debug("GET /projects userId={} status={} categoryId={} size={} includeArchived={} page={} pageSize={}",
                userId.value(), status, categoryId, size, includeArchived, page, pageSize);
        return ProjectPageResponse.from(
                projects.list(userId, status, categoryId, size, includeArchived, page, pageSize, sort));
    }

    @GetMapping("/projects/priority-ordered")
    public List<ProjectResponse> priorityOrdered(@CurrentUser UserId userId) {
        log.debug("GET /projects/priority-ordered userId={}", userId.value());
        return ProjectResponse.from(projects.listByPriority(userId));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@CurrentUser UserId userId, @Valid @RequestBody CreateProjectRequest request) {
        log.debug("POST /projects userId={}", userId.value());
        return ProjectResponse.from(projects.create(userId, new CreateCommand(
                request.categoryId(), request.name(), request.description(), request.size(),
                request.color(), request.startDate(), request.endDate())));
    }

    @GetMapping("/projects/{id}")
    public ProjectResponse get(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("GET /projects/{} userId={}", id, userId.value());
        return ProjectResponse.from(projects.get(userId, id));
    }

    @PatchMapping("/projects/{id}")
    public ProjectResponse patch(@CurrentUser UserId userId, @PathVariable UUID id,
            @Valid @RequestBody PatchProjectRequest request) {
        log.debug("PATCH /projects/{} userId={}", id, userId.value());
        return ProjectResponse.from(projects.patch(userId, id, new PatchCommand(
                request.categoryId(), request.name(), request.description(), request.status(), request.size(),
                request.color(), request.startDate(), request.endDate(), request.actualStart(),
                request.actualEnd(), request.expectedVersion())));
    }

    @DeleteMapping("/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("DELETE /projects/{} userId={}", id, userId.value());
        projects.delete(userId, id);
    }

    @PostMapping("/projects:reorder")
    public List<ProjectResponse> reorder(@CurrentUser UserId userId, @Valid @RequestBody ReorderRequest request) {
        log.debug("POST /projects:reorder userId={} count={}", userId.value(), request.orderedIds().size());
        return ProjectResponse.from(projects.reorder(userId, request.orderedIds()));
    }
}
