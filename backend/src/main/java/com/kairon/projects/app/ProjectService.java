package com.kairon.projects.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectPage;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectSize;
import com.kairon.projects.domain.ProjectStatus;
import com.kairon.projects.repo.ProjectCategoryRepository;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The project module's application service: filtered/paginated list, the
 * flattened priority view, CRUD, {@code :reorder}, and delete-with-cascade.
 * Every method takes a {@link UserId} and 404s (never 403) on someone else's
 * row via {@link ApiException#notFound}, per docs/DESIGN.md §3.2.
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final int POSITION_GAP = 100;
    private static final String DEFAULT_COLOR = "#6366f1";

    private final ProjectRepository projects;
    private final ProjectCategoryRepository categories;
    private final ProjectTaskRepository tasks;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectCategoryRepository categories,
            ProjectTaskRepository tasks, Clock clock) {
        this.projects = projects;
        this.categories = categories;
        this.tasks = tasks;
        this.clock = clock;
    }

    public record CreateCommand(
            UUID categoryId, String name, String description, String size, String color,
            LocalDate startDate, LocalDate endDate) {
    }

    /** Whole-form overwrite (D15) — excludes {@code priorityRank}, which only changes via {@link #reorder}. */
    public record PatchCommand(
            UUID categoryId, String name, String description, String status, String size, String color,
            LocalDate startDate, LocalDate endDate, LocalDate actualStart, LocalDate actualEnd,
            Long expectedVersion) {
    }

    public ProjectPage list(UserId userId, String status, UUID categoryId, String size, boolean includeArchived,
            int page, int pageSize, List<String> sort) {
        ProjectStatus statusFilter = status != null ? parseStatus(status) : null;
        ProjectSize sizeFilter = size != null ? parseSize(size) : null;
        Pageable pageable = PageRequest.of(page, pageSize, SortParsing.parse(sort));
        Page<Project> result = statusFilter != null || includeArchived
                ? projects.search(userId.value(), statusFilter, categoryId, sizeFilter, pageable)
                : projects.searchExcludingStatus(userId.value(), ProjectStatus.ARCHIVED, categoryId, sizeFilter,
                        pageable);
        List<ProjectView> views = result.getContent().stream().map(ProjectMapper::toView).toList();
        log.debug("Listed {} project(s) userId={} status={} categoryId={} size={} includeArchived={} page={}",
                views.size(), userId.value(), status, categoryId, size, includeArchived, result.getNumber());
        return new ProjectPage(views, result.getNumber(), result.getTotalElements());
    }

    public List<ProjectView> listByPriority(UserId userId) {
        List<ProjectView> views = rankedSet(userId.value()).stream().map(ProjectMapper::toView).toList();
        log.debug("Listed {} project(s) by priority userId={}", views.size(), userId.value());
        return views;
    }

    public ProjectView get(UserId userId, UUID id) {
        return ProjectMapper.toView(require(userId, id));
    }

    @Transactional
    public ProjectView create(UserId userId, CreateCommand command) {
        String name = requireName(command.name());
        if (command.categoryId() != null) {
            requireCategory(userId, command.categoryId());
        }
        ProjectSize size = command.size() != null ? parseSize(command.size()) : null;
        String color = command.color() != null ? command.color() : DEFAULT_COLOR;
        int priorityRank = nextPriorityRank(userId.value());
        Project saved = projects.save(Project.create(userId.value(), command.categoryId(), name,
                trimToNull(command.description()), color, size, priorityRank,
                command.startDate(), command.endDate()));
        log.info("Created project {} userId={} priorityRank={}", saved.getId(), userId.value(), priorityRank);
        return ProjectMapper.toView(saved);
    }

    @Transactional
    public ProjectView patch(UserId userId, UUID id, PatchCommand command) {
        Project project = require(userId, id);
        if (command.expectedVersion() != null && command.expectedVersion() != project.getVersion()) {
            log.warn("Patch rejected: version conflict on project {} userId={} (expected {}, actual {})",
                    id, userId.value(), command.expectedVersion(), project.getVersion());
            throw ApiException.conflict(
                    "This project was modified by another request. Reload and try again.");
        }
        if (command.categoryId() != null) {
            requireCategory(userId, command.categoryId());
        }
        String name = requireName(command.name());
        ProjectStatus status = parseStatus(command.status());
        ProjectSize size = command.size() != null ? parseSize(command.size()) : null;
        String color = command.color() != null ? command.color() : project.getColor();
        project.edit(command.categoryId(), name, trimToNull(command.description()), status, size, color,
                command.startDate(), command.endDate(), command.actualStart(), command.actualEnd());
        log.info("Patched project {} userId={}", id, userId.value());
        return ProjectMapper.toView(project);
    }

    @Transactional
    public List<ProjectView> reorder(UserId userId, List<UUID> orderedIds) {
        List<Project> current = rankedSet(userId.value());
        Map<UUID, Project> byId = new HashMap<>();
        for (Project project : current) {
            byId.put(project.getId(), project);
        }
        if (orderedIds.size() != byId.size() || !byId.keySet().equals(new HashSet<>(orderedIds))) {
            log.warn("Reorder rejected: userId={} sent {} id(s), user holds {} rankable project(s)",
                    userId.value(), orderedIds.size(), byId.size());
            throw ApiException.badRequest(
                    "`orderedIds` must list exactly the user's current non-archived projects.");
        }
        int rank = POSITION_GAP;
        List<ProjectView> result = new java.util.ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            Project project = byId.get(id);
            project.moveTo(rank);
            result.add(ProjectMapper.toView(project));
            rank += POSITION_GAP;
        }
        log.info("Reordered {} project(s) userId={}", result.size(), userId.value());
        return result;
    }

    @Transactional
    public void delete(UserId userId, UUID id) {
        Project project = require(userId, id);
        project.softDelete(clock.instant());
        int cascaded = 0;
        for (var task : tasks.findByProjectIdAndDeletedAtIsNull(id)) {
            task.softDelete(clock.instant());
            cascaded++;
        }
        log.info("Soft-deleted project {} userId={}, cascaded to {} task(s)", id, userId.value(), cascaded);
    }

    private Project require(UserId userId, UUID id) {
        return projects.findByIdAndUserIdAndDeletedAtIsNull(id, userId.value())
                .orElseThrow(() -> {
                    log.debug("Project {} not visible to userId={} (missing, deleted, or foreign)",
                            id, userId.value());
                    return ApiException.notFound("Project not found.");
                });
    }

    private void requireCategory(UserId userId, UUID categoryId) {
        if (categories.findByIdAndUserId(categoryId, userId.value()).isEmpty()) {
            log.debug("Category {} not visible to userId={} (missing or foreign)", categoryId, userId.value());
            throw ApiException.notFound("Project category not found.");
        }
    }

    private List<Project> rankedSet(UUID userId) {
        return projects.findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
                userId, ProjectStatus.ARCHIVED);
    }

    private int nextPriorityRank(UUID userId) {
        return rankedSet(userId).stream()
                .mapToInt(Project::getPriorityRank)
                .max()
                .orElse(0) + POSITION_GAP;
    }

    private static ProjectStatus parseStatus(String raw) {
        try {
            return ProjectStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status: " + raw);
        }
    }

    private static ProjectSize parseSize(String raw) {
        try {
            return ProjectSize.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown size: " + raw);
        }
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("Name must not be blank.");
        }
        if (trimmed.length() > 200) {
            throw ApiException.badRequest("Name must be at most 200 characters.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
