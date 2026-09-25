package com.kairon.projects.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi.PlannedDependency;
import com.kairon.projects.api.ProjectsApi.PlannedTask;
import com.kairon.projects.api.ProjectsApi.ProjectPlanCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a whole project (project + task tree + dependency edges) from a
 * {@link ProjectPlanCommand} in one transaction, by calling {@link ProjectService},
 * {@link ProjectTaskService}, and {@link TaskDependencyService} as ordinary
 * collaborators — the same entity-construction/validation code a manual
 * create/PATCH already goes through, not a second copy of those rules
 * (docs/milestones/M8.5-project-generation.md D3). The model never chooses a
 * category, color, or status (D10) — {@link ProjectService#create} is called
 * with a null category/color, leaving the project uncategorized with the
 * default color and {@code PLANNING} status.
 */
@Service
class ProjectPlanImportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPlanImportService.class);

    private final ProjectService projectService;
    private final ProjectTaskService taskService;
    private final TaskDependencyService dependencyService;

    ProjectPlanImportService(ProjectService projectService, ProjectTaskService taskService,
            TaskDependencyService dependencyService) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.dependencyService = dependencyService;
    }

    @Transactional
    ProjectView createFromPlan(UserId userId, ProjectPlanCommand command) {
        ProjectView project = projectService.create(userId, new ProjectService.CreateCommand(
                null, command.name(), command.description(), command.size(), null,
                command.startDate(), command.endDate()));

        Map<String, UUID> idByKey = new HashMap<>();
        List<PlannedTask> ordered = topLevelFirst(flattenExcessiveNesting(command.tasks()));
        for (PlannedTask t : ordered) {
            UUID parentId = t.parentKey() == null ? null : idByKey.get(t.parentKey());
            ProjectTaskView created = taskService.create(userId, project.id(),
                    new ProjectTaskService.CreateCommand(t.name(), t.description(), parentId,
                            t.plannedStart(), t.plannedEnd(), t.estimateHours(), t.isMilestone()));
            idByKey.put(t.key(), created.id());
        }

        int dropped = 0;
        for (PlannedDependency d : command.dependencies()) {
            UUID predecessorId = idByKey.get(d.predecessorKey());
            UUID successorId = idByKey.get(d.successorKey());
            if (predecessorId == null || successorId == null) {
                log.warn("Dropped dependency {} -> {} for project {}: unknown/excluded key, userId={}",
                        d.predecessorKey(), d.successorKey(), project.id(), userId.value());
                dropped++;
                continue;
            }
            try {
                dependencyService.create(userId, successorId,
                        new TaskDependencyService.CreateCommand(predecessorId, d.type(), d.lagDays()));
            } catch (ApiException e) {
                log.warn("Dropped dependency {} -> {} for project {}: {}",
                        predecessorId, successorId, project.id(), e.getMessage());
                dropped++;
            }
        }
        log.info("Created project {} from plan userId={} tasks={} dependencies={} dropped={}",
                project.id(), userId.value(), idByKey.size(), command.dependencies().size(), dropped);
        return projectService.get(userId, project.id());
    }

    /**
     * D11: a task whose {@code parentKey} points at another non-root task (a
     * 3-level chain) is flattened to top-level, not rejected — the ≤2-level
     * rule is enforced by {@code ProjectTaskService#validateParent}, which
     * would otherwise 400 the whole run over one over-nested task. A
     * {@code parentKey} naming an unknown/excluded key is likewise flattened,
     * rather than left to resolve to a stray {@code null} parent id below.
     */
    private List<PlannedTask> flattenExcessiveNesting(List<PlannedTask> tasks) {
        Set<String> topLevelKeys = tasks.stream()
                .filter(t -> t.parentKey() == null)
                .map(PlannedTask::key)
                .collect(Collectors.toSet());
        List<PlannedTask> result = new ArrayList<>(tasks.size());
        for (PlannedTask t : tasks) {
            if (t.parentKey() != null && !topLevelKeys.contains(t.parentKey())) {
                log.warn("Flattened task '{}' to top-level: parent '{}' is not a top-level task",
                        t.key(), t.parentKey());
                result.add(new PlannedTask(t.key(), null, t.name(), t.description(), t.isMilestone(),
                        t.plannedStart(), t.plannedEnd(), t.estimateHours()));
            } else {
                result.add(t);
            }
        }
        return result;
    }

    /** Stable partition — roots then children — sufficient since depth is capped at 2 levels. */
    private List<PlannedTask> topLevelFirst(List<PlannedTask> tasks) {
        List<PlannedTask> result = new ArrayList<>(tasks.size());
        for (PlannedTask t : tasks) {
            if (t.parentKey() == null) {
                result.add(t);
            }
        }
        for (PlannedTask t : tasks) {
            if (t.parentKey() != null) {
                result.add(t);
            }
        }
        return result;
    }
}
