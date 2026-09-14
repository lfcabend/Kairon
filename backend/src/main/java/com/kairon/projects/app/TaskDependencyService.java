package com.kairon.projects.app;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.TaskResolution.TaskAndProject;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.domain.TaskDependency;
import com.kairon.projects.domain.TaskDependencyType;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;
import com.kairon.projects.repo.TaskDependencyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dependency edges between a project's tasks: list with the computed
 * FS-violation flag (D13), create with same-project/self/duplicate/cycle
 * validation (D3/D4), delete. Every method resolves ownership via the task(s)
 * it touches, using {@link TaskResolution} where there's no project id in the
 * URL — see docs/milestones/M5-gantt-dependencies.md §4.4.
 */
@Service
@Transactional(readOnly = true)
public class TaskDependencyService {

    private static final Logger log = LoggerFactory.getLogger(TaskDependencyService.class);

    private final TaskDependencyRepository dependencies;
    private final ProjectTaskRepository tasks;
    private final ProjectRepository projects;

    public TaskDependencyService(TaskDependencyRepository dependencies, ProjectTaskRepository tasks,
            ProjectRepository projects) {
        this.dependencies = dependencies;
        this.tasks = tasks;
        this.projects = projects;
    }

    public record CreateCommand(UUID predecessorId, String type, Integer lagDays) {
    }

    public List<TaskDependencyView> list(UserId userId, UUID projectId) {
        requireProject(userId, projectId);
        Map<UUID, ProjectTask> byId = tasks.findByProjectIdAndDeletedAtIsNull(projectId).stream()
                .collect(Collectors.toMap(ProjectTask::getId, t -> t));
        List<TaskDependencyView> views = dependencies.findByProjectId(projectId).stream()
                .map(d -> TaskDependencyMapper.toView(d, violatesConstraint(d, byId)))
                .toList();
        log.debug("Listed {} dependency edge(s) userId={} projectId={}", views.size(), userId.value(), projectId);
        return views;
    }

    @Transactional
    public TaskDependencyView create(UserId userId, UUID successorTaskId, CreateCommand command) {
        TaskAndProject resolved = TaskResolution.requireTaskWithProject(tasks, projects, userId, successorTaskId);
        Project project = resolved.project();
        UUID predecessorId = command.predecessorId();
        if (predecessorId.equals(successorTaskId)) {
            log.warn("Dependency create rejected: task {} can't depend on itself userId={}",
                    successorTaskId, userId.value());
            throw ApiException.badRequest("A task can't depend on itself.");
        }
        tasks.findByIdAndProjectIdAndDeletedAtIsNull(predecessorId, project.getId())
                .orElseThrow(() -> {
                    log.warn("Dependency create rejected: predecessor {} not in project {} userId={}",
                            predecessorId, project.getId(), userId.value());
                    return ApiException.badRequest("Predecessor task not found in this project.");
                });
        if (dependencies.findByPredecessorIdAndSuccessorId(predecessorId, successorTaskId).isPresent()) {
            log.warn("Dependency create rejected: duplicate edge {} -> {} userId={}",
                    predecessorId, successorTaskId, userId.value());
            throw ApiException.conflict("This dependency already exists.");
        }
        if (createsCycle(project.getId(), predecessorId, successorTaskId)) {
            log.warn("Dependency create rejected: {} -> {} would create a cycle userId={}",
                    predecessorId, successorTaskId, userId.value());
            throw ApiException.badRequest("This would create a circular dependency.");
        }
        TaskDependencyType type = parseType(command.type());
        int lagDays = command.lagDays() != null ? command.lagDays() : 0;
        TaskDependency saved = dependencies.save(TaskDependency.create(predecessorId, successorTaskId, type, lagDays));
        log.info("Created dependency {} ({} -> {}) userId={} projectId={}",
                saved.getId(), predecessorId, successorTaskId, userId.value(), project.getId());
        Map<UUID, ProjectTask> byId = tasks.findByProjectIdAndDeletedAtIsNull(project.getId()).stream()
                .collect(Collectors.toMap(ProjectTask::getId, t -> t));
        return TaskDependencyMapper.toView(saved, violatesConstraint(saved, byId));
    }

    @Transactional
    public void delete(UserId userId, UUID dependencyId) {
        TaskDependency dependency = dependencies.findById(dependencyId)
                .orElseThrow(() -> ApiException.notFound("Dependency not found."));
        TaskResolution.requireTaskWithProject(tasks, projects, userId, dependency.getSuccessorId());
        dependencies.deleteById(dependencyId);
        log.info("Deleted dependency {} userId={}", dependencyId, userId.value());
    }

    private void requireProject(UserId userId, UUID projectId) {
        projects.findByIdAndUserIdAndDeletedAtIsNull(projectId, userId.value())
                .orElseThrow(() -> ApiException.notFound("Project not found."));
    }

    /** D13: FS-only for now (Q4); other types and missing dates never report a violation. */
    private static boolean violatesConstraint(TaskDependency d, Map<UUID, ProjectTask> byId) {
        if (d.getType() != TaskDependencyType.FS) {
            return false;
        }
        ProjectTask predecessor = byId.get(d.getPredecessorId());
        ProjectTask successor = byId.get(d.getSuccessorId());
        if (predecessor == null || successor == null
                || predecessor.getPlannedEnd() == null || successor.getPlannedStart() == null) {
            return false;
        }
        return successor.getPlannedStart().isBefore(predecessor.getPlannedEnd().plusDays(d.getLagDays()));
    }

    /** D3: DFS from the new edge's successor — if the predecessor is already reachable, it's a cycle. */
    private boolean createsCycle(UUID projectId, UUID predecessorId, UUID successorId) {
        Map<UUID, List<UUID>> adjacency = dependencies.findByProjectId(projectId).stream()
                .collect(Collectors.groupingBy(TaskDependency::getPredecessorId,
                        Collectors.mapping(TaskDependency::getSuccessorId, Collectors.toList())));
        Deque<UUID> stack = new ArrayDeque<>(List.of(successorId));
        Set<UUID> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (current.equals(predecessorId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            stack.addAll(adjacency.getOrDefault(current, List.of()));
        }
        return false;
    }

    private static TaskDependencyType parseType(String raw) {
        if (raw == null) {
            return TaskDependencyType.FS;
        }
        try {
            return TaskDependencyType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown dependency type: " + raw);
        }
    }
}
