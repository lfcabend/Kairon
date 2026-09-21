package com.kairon.projects.app;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskPage;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.app.TaskResolution.TaskAndProject;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.domain.ProjectTaskStatus;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;
import com.kairon.projects.repo.TaskDependencyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The task module's application service: the project's task tree, CRUD,
 * {@code :reorder}, delete-with-one-level-cascade (D8/Q4), and the
 * {@link ProjectsApi} port. Every method resolves the <b>project</b> first
 * under {@link UserId} (404 if missing/foreign), then scopes the task by
 * {@code projectId} — {@code project_task} carries no {@code user_id} of its
 * own (docs/DATA_MODEL.md).
 */
@Service
@Transactional(readOnly = true)
public class ProjectTaskService implements ProjectsApi {

    private static final Logger log = LoggerFactory.getLogger(ProjectTaskService.class);
    private static final int POSITION_GAP = 100;

    private final ProjectTaskRepository tasks;
    private final ProjectRepository projects;
    private final TaskDependencyRepository dependencies;
    private final ProjectsProperties properties;
    private final Clock clock;

    public ProjectTaskService(ProjectTaskRepository tasks, ProjectRepository projects,
            TaskDependencyRepository dependencies, ProjectsProperties properties, Clock clock) {
        this.tasks = tasks;
        this.projects = projects;
        this.dependencies = dependencies;
        this.properties = properties;
        this.clock = clock;
    }

    public record CreateCommand(
            String name, String description, UUID parentTaskId, LocalDate plannedStart, LocalDate plannedEnd,
            BigDecimal estimateHours, boolean isMilestone) {
    }

    /** Whole-form overwrite (D15). */
    public record PatchCommand(
            String name, String description, String status, UUID parentTaskId, LocalDate plannedStart,
            LocalDate plannedEnd, BigDecimal estimateHours, BigDecimal actualHours, int progressPercent,
            boolean isMilestone, Long expectedVersion) {
    }

    public ProjectTaskPage list(UserId userId, UUID projectId, Integer page, Integer size, List<String> sort) {
        Project project = requireProject(userId, projectId);
        Pageable pageable = PageRequest.of(page != null ? page : 0,
                size != null ? size : properties.taskListDefaultSize(), SortParsing.parse(sort));
        Page<ProjectTask> result = tasks
                .findByProjectIdAndDeletedAtIsNullOrderByParentTaskIdAscPositionAsc(projectId, pageable);
        List<ProjectTaskView> views = result.getContent().stream()
                .map(t -> ProjectTaskMapper.toView(t, project))
                .toList();
        log.debug("Listed {} task(s) userId={} projectId={}", views.size(), userId.value(), projectId);
        return new ProjectTaskPage(views, result.getNumber(), result.getTotalElements());
    }

    @Transactional
    public ProjectTaskView create(UserId userId, UUID projectId, CreateCommand command) {
        Project project = requireProject(userId, projectId);
        String name = requireName(command.name());
        validateParent(projectId, command.parentTaskId());
        int position = nextPosition(projectId, command.parentTaskId());
        ProjectTask task = ProjectTask.create(projectId, command.parentTaskId(), name,
                trimToNull(command.description()), position);
        applySchedule(task, command.isMilestone(), command.plannedStart(), command.plannedEnd());
        if (command.estimateHours() != null) {
            task.setEstimateHours(command.estimateHours());
        }
        ProjectTask saved = tasks.save(task);
        log.info("Created task {} userId={} projectId={} parentTaskId={}",
                saved.getId(), userId.value(), projectId, command.parentTaskId());
        return ProjectTaskMapper.toView(saved, project);
    }

    @Transactional
    public ProjectTaskView patch(UserId userId, UUID taskId, PatchCommand command) {
        TaskAndProject resolved = TaskResolution.requireTaskWithProject(tasks, projects, userId, taskId);
        ProjectTask task = resolved.task();
        Project project = resolved.project();
        if (command.expectedVersion() != null && command.expectedVersion() != task.getVersion()) {
            log.warn("Patch rejected: version conflict on task {} userId={} (expected {}, actual {})",
                    taskId, userId.value(), command.expectedVersion(), task.getVersion());
            throw ApiException.conflict(
                    "This task was modified by another request. Reload and try again.");
        }
        if (command.parentTaskId() != null) {
            if (command.parentTaskId().equals(taskId)) {
                throw ApiException.badRequest("A task can't be its own parent.");
            }
            validateParent(project.getId(), command.parentTaskId());
            if (tasks.existsByParentTaskIdAndDeletedAtIsNull(taskId)) {
                throw ApiException.badRequest("This task has subtasks and can't be nested under another task.");
            }
        }
        String name = requireName(command.name());
        ProjectTaskStatus status = parseStatus(command.status());
        task.rename(name);
        task.editDescription(trimToNull(command.description()));
        task.changeStatus(status);
        task.reparent(command.parentTaskId());
        try {
            applySchedule(task, command.isMilestone(), command.plannedStart(), command.plannedEnd());
            task.setProgress(command.progressPercent());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        task.setEstimateHours(command.estimateHours());
        task.setActualHours(command.actualHours());
        // Flush now so the @Version bump (which Hibernate otherwise defers to commit, after this
        // method returns) is visible on `task` before it's mapped — the response's `version` is the
        // `expectedVersion` the client sends on its *next* PATCH, so a stale value here made every
        // second edit of the same row fail with a false optimistic-lock conflict.
        tasks.saveAndFlush(task);
        log.info("Patched task {} userId={} projectId={}", taskId, userId.value(), project.getId());
        return ProjectTaskMapper.toView(task, project);
    }

    @Transactional
    public List<ProjectTaskView> reorder(UserId userId, UUID projectId, UUID parentTaskId, List<UUID> orderedIds) {
        Project project = requireProject(userId, projectId);
        List<ProjectTask> current = tasks
                .findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(projectId, parentTaskId);
        Map<UUID, ProjectTask> byId = new HashMap<>();
        for (ProjectTask task : current) {
            byId.put(task.getId(), task);
        }
        if (orderedIds.size() != byId.size() || !byId.keySet().equals(new HashSet<>(orderedIds))) {
            log.warn("Reorder rejected: userId={} projectId={} parentTaskId={} sent {} id(s), sibling group holds {}",
                    userId.value(), projectId, parentTaskId, orderedIds.size(), byId.size());
            throw ApiException.badRequest("`orderedIds` must list exactly that sibling group's current members.");
        }
        int position = POSITION_GAP;
        List<ProjectTaskView> result = new java.util.ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            ProjectTask task = byId.get(id);
            task.moveTo(position);
            position += POSITION_GAP;
        }
        // One flush for the whole batch (see the comment in patch()) so every returned view's
        // version already reflects its bump, instead of the client's next edit of any of these
        // rows getting a false optimistic-lock conflict.
        tasks.flush();
        for (UUID id : orderedIds) {
            result.add(ProjectTaskMapper.toView(byId.get(id), project));
        }
        log.info("Reordered {} task(s) userId={} projectId={} parentTaskId={}",
                result.size(), userId.value(), projectId, parentTaskId);
        return result;
    }

    @Transactional
    public void delete(UserId userId, UUID taskId) {
        TaskAndProject resolved = TaskResolution.requireTaskWithProject(tasks, projects, userId, taskId);
        ProjectTask task = resolved.task();
        Project project = resolved.project();
        task.softDelete(clock.instant());
        dependencies.deleteAllForTask(taskId);
        int cascaded = 0;
        for (ProjectTask child : tasks
                .findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(project.getId(), taskId)) {
            child.softDelete(clock.instant());
            dependencies.deleteAllForTask(child.getId());
            cascaded++;
        }
        log.info("Soft-deleted task {} userId={} projectId={}, cascaded to {} subtask(s)",
                taskId, userId.value(), project.getId(), cascaded);
    }

    // --- ProjectsApi port -------------------------------------------------------

    @Override
    public ProjectTaskView requireTask(UserId userId, UUID taskId) {
        TaskAndProject resolved = TaskResolution.requireTaskWithProject(tasks, projects, userId, taskId);
        return ProjectTaskMapper.toView(resolved.task(), resolved.project());
    }

    @Override
    public List<ProjectTaskView> dueOrOverdue(UserId userId, LocalDate day) {
        List<ProjectTask> due = tasks.findDueOrOverdue(userId.value(), day);
        Map<UUID, Project> byProjectId = new HashMap<>();
        for (Project project : projects.findAllById(due.stream().map(ProjectTask::getProjectId).distinct().toList())) {
            byProjectId.put(project.getId(), project);
        }
        List<ProjectTaskView> views = due.stream()
                .map(t -> ProjectTaskMapper.toView(t, byProjectId.get(t.getProjectId())))
                .toList();
        log.debug("Found {} due/overdue task(s) userId={} day={}", views.size(), userId.value(), day);
        return views;
    }

    // --- internals -----------------------------------------------------------

    private Project requireProject(UserId userId, UUID projectId) {
        return projects.findByIdAndUserIdAndDeletedAtIsNull(projectId, userId.value())
                .orElseThrow(() -> {
                    log.debug("Project {} not visible to userId={} (missing, deleted, or foreign)",
                            projectId, userId.value());
                    return ApiException.notFound("Project not found.");
                });
    }

    /** Depth rule (§4.4): the referenced parent must exist in this project and itself be top-level. */
    private void validateParent(UUID projectId, UUID parentTaskId) {
        if (parentTaskId == null) {
            return;
        }
        ProjectTask parent = tasks.findByIdAndProjectIdAndDeletedAtIsNull(parentTaskId, projectId)
                .orElseThrow(() -> ApiException.badRequest("Parent task not found in this project."));
        if (parent.getParentTaskId() != null) {
            throw ApiException.badRequest("A subtask can't have its own subtasks.");
        }
    }

    /** Applies the milestone/date invariant (D9) to a task being created or patched. */
    private void applySchedule(ProjectTask task, boolean isMilestone, LocalDate plannedStart, LocalDate plannedEnd) {
        if (isMilestone) {
            LocalDate date = plannedStart != null ? plannedStart : plannedEnd;
            if (date == null) {
                throw ApiException.badRequest("A milestone requires plannedStart or plannedEnd.");
            }
            task.markMilestone(date);
        } else {
            task.unmarkMilestone();
            task.reschedule(plannedStart, plannedEnd);
        }
    }

    private int nextPosition(UUID projectId, UUID parentTaskId) {
        return tasks.findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(projectId, parentTaskId)
                .stream()
                .mapToInt(ProjectTask::getPosition)
                .max()
                .orElse(0) + POSITION_GAP;
    }

    private static ProjectTaskStatus parseStatus(String raw) {
        try {
            return ProjectTaskStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status: " + raw);
        }
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("Name must not be blank.");
        }
        if (trimmed.length() > 300) {
            throw ApiException.badRequest("Name must be at most 300 characters.");
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
