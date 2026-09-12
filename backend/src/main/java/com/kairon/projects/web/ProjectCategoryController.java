package com.kairon.projects.web;

import java.util.List;
import java.util.UUID;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.ProjectCategoryService;
import com.kairon.projects.app.ProjectCategoryService.CreateCommand;
import com.kairon.projects.app.ProjectCategoryService.PatchCommand;
import com.kairon.projects.web.ProjectCategoryDtos.CreateProjectCategoryRequest;
import com.kairon.projects.web.ProjectCategoryDtos.PatchProjectCategoryRequest;
import com.kairon.projects.web.ProjectCategoryDtos.ProjectCategoryResponse;
import com.kairon.projects.web.ProjectCategoryDtos.ReorderRequest;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/project-categories} — category CRUD + {@code :reorder}
 * (D12/D13). The class mapping is {@code /api/v1} rather than
 * {@code /api/v1/project-categories} so the AIP-style
 * {@code /project-categories:reorder} resolves — Spring's path combiner would
 * otherwise insert a slash before the colon.
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectCategoryController {

    private static final Logger log = LoggerFactory.getLogger(ProjectCategoryController.class);

    private final ProjectCategoryService categories;

    public ProjectCategoryController(ProjectCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/project-categories")
    public List<ProjectCategoryResponse> list(@CurrentUser UserId userId) {
        log.debug("GET /project-categories userId={}", userId.value());
        return ProjectCategoryResponse.from(categories.list(userId));
    }

    @PostMapping("/project-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectCategoryResponse create(@CurrentUser UserId userId,
            @Valid @RequestBody CreateProjectCategoryRequest request) {
        log.debug("POST /project-categories userId={}", userId.value());
        return ProjectCategoryResponse.from(
                categories.create(userId, new CreateCommand(request.name(), request.color())));
    }

    @PatchMapping("/project-categories/{id}")
    public ProjectCategoryResponse patch(@CurrentUser UserId userId, @PathVariable UUID id,
            @Valid @RequestBody PatchProjectCategoryRequest request) {
        log.debug("PATCH /project-categories/{} userId={}", id, userId.value());
        return ProjectCategoryResponse.from(categories.patch(userId, id,
                new PatchCommand(request.name(), request.color(), request.expectedVersion())));
    }

    @DeleteMapping("/project-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("DELETE /project-categories/{} userId={}", id, userId.value());
        categories.delete(userId, id);
    }

    @PostMapping("/project-categories:reorder")
    public List<ProjectCategoryResponse> reorder(@CurrentUser UserId userId,
            @Valid @RequestBody ReorderRequest request) {
        log.debug("POST /project-categories:reorder userId={} count={}",
                userId.value(), request.orderedIds().size());
        return ProjectCategoryResponse.from(categories.reorder(userId, request.orderedIds()));
    }
}
