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
import com.kairon.projects.api.ProjectsApi.DependencyOperation;
import com.kairon.projects.api.ProjectsApi.ProjectEditCommand;
import com.kairon.projects.api.ProjectsApi.ProjectFieldChanges;
import com.kairon.projects.api.ProjectsApi.ReorderOperation;
import com.kairon.projects.api.ProjectsApi.TaskOperation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies an accepted {@link ProjectEditCommand} to an existing project in
 * one transaction, by calling {@link ProjectService}, {@link ProjectTaskService},
 * and {@link TaskDependencyService} as ordinary collaborators — the same
 * reuse discipline {@link ProjectPlanImportService} established for whole-
 * project creation (M8.5 D3), applied here to a diff against existing data
 * (docs/milestones/M9.5-ai-project-editing.md D1/D8). A reference that no
 * longer resolves (a stale task/dependency id, an unknown same-diff key, a
 * rejected dependency) is dropped and logged at {@code warn}, never failing
 * the whole apply.
 */
@Service
class ProjectEditApplyService {

    private static final Logger log = LoggerFactory.getLogger(ProjectEditApplyService.class);

    private final ProjectService projectService;
    private final ProjectTaskService taskService;
    private final TaskDependencyService dependencyService;
    private final CategoryResolver categoryResolver;

    ProjectEditApplyService(ProjectService projectService, ProjectTaskService taskService,
            TaskDependencyService dependencyService, CategoryResolver categoryResolver) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.dependencyService = dependencyService;
        this.categoryResolver = categoryResolver;
    }

    @Transactional
    ProjectView applyProjectEdit(UserId userId, UUID projectId, ProjectEditCommand command) {
        if (command.projectChanges() != null) {
            applyProjectChanges(userId, projectId, command.projectChanges());
        }

        int removedTasks = applyTaskRemovals(userId, command.taskOperations());
        Map<String, UUID> idByKey = applyTaskAdditions(userId, projectId, command.taskOperations());
        int updatedTasks = applyTaskUpdates(userId, idByKey, command.taskOperations());
        int reordered = applyReorders(userId, projectId, idByKey, command.reorderOperations());
        int removedDeps = applyDependencyRemovals(userId, command.dependencyOperations());
        int addedDeps = applyDependencyAdditions(userId, idByKey, command.dependencyOperations());

        log.info("Applied project edit userId={} projectId={} tasksAdded={} tasksUpdated={} tasksRemoved={} "
                        + "dependenciesAdded={} dependenciesRemoved={} reorderGroups={}",
                userId.value(), projectId, idByKey.size(), updatedTasks, removedTasks, addedDeps, removedDeps,
                reordered);
        return projectService.get(userId, projectId);
    }

    /**
     * The system prompt asks the model to echo every field it isn't
     * intentionally changing, but nothing enforces that it actually did —
     * falling back to the project's own current value for anything the diff
     * left {@code null} keeps a partially-filled {@code projectChanges}
     * (e.g. only a category change) safe to apply, same tolerant posture as
     * every other model-output edge case in this milestone (D1).
     */
    private void applyProjectChanges(UserId userId, UUID projectId, ProjectFieldChanges changes) {
        ProjectView current = projectService.get(userId, projectId);
        UUID categoryId = categoryResolver.resolve(userId, changes.categoryId(), changes.newCategoryName());
        String name = changes.name() != null ? changes.name() : current.name();
        String description = changes.description() != null ? changes.description() : current.description();
        String size = changes.size() != null ? changes.size() : current.size();
        java.time.LocalDate startDate = changes.startDate() != null ? changes.startDate() : current.startDate();
        java.time.LocalDate endDate = changes.endDate() != null ? changes.endDate() : current.endDate();
        projectService.patch(userId, projectId, new ProjectService.PatchCommand(
                categoryId, name, description, current.status(), size, current.color(), startDate, endDate,
                current.actualStart(), current.actualEnd(), null));
    }

    private int applyTaskRemovals(UserId userId, List<TaskOperation> operations) {
        int count = 0;
        for (TaskOperation op : operations) {
            if (!"REMOVE".equals(op.op())) {
                continue;
            }
            try {
                taskService.delete(userId, op.existingTaskId());
                count++;
            } catch (ApiException e) {
                log.warn("Dropped task REMOVE for {}: {}", op.existingTaskId(), e.getMessage());
            }
        }
        return count;
    }

    /** Parent-before-child within the new set, mirroring {@code ProjectPlanImportService#topLevelFirst}. */
    private Map<String, UUID> applyTaskAdditions(UserId userId, UUID projectId, List<TaskOperation> operations) {
        List<TaskOperation> adds = operations.stream().filter(op -> "ADD".equals(op.op())).toList();
        Set<String> newKeys = adds.stream().map(TaskOperation::key).collect(Collectors.toSet());
        Map<String, UUID> idByKey = new HashMap<>();
        for (TaskOperation op : orderNewParentsFirst(adds, newKeys)) {
            UUID parentId = resolveRef(op.parentRef(), idByKey);
            try {
                ProjectTaskView created = taskService.create(userId, projectId, new ProjectTaskService.CreateCommand(
                        op.name(), op.description(), parentId, op.plannedStart(), op.plannedEnd(),
                        op.estimateHours(), op.isMilestone()));
                idByKey.put(op.key(), created.id());
            } catch (ApiException e) {
                log.warn("Dropped task ADD '{}': {}", op.key(), e.getMessage());
            }
        }
        return idByKey;
    }

    private List<TaskOperation> orderNewParentsFirst(List<TaskOperation> adds, Set<String> newKeys) {
        List<TaskOperation> result = new ArrayList<>(adds.size());
        for (TaskOperation op : adds) {
            if (op.parentRef() == null || !newKeys.contains(op.parentRef())) {
                result.add(op);
            }
        }
        for (TaskOperation op : adds) {
            if (op.parentRef() != null && newKeys.contains(op.parentRef())) {
                result.add(op);
            }
        }
        return result;
    }

    private int applyTaskUpdates(UserId userId, Map<String, UUID> idByKey, List<TaskOperation> operations) {
        int count = 0;
        for (TaskOperation op : operations) {
            if (!"UPDATE".equals(op.op())) {
                continue;
            }
            try {
                ProjectTaskView current = taskService.requireTask(userId, op.existingTaskId());
                UUID parentId = resolveRef(op.parentRef(), idByKey);
                taskService.patch(userId, op.existingTaskId(), new ProjectTaskService.PatchCommand(
                        op.name(), op.description(), current.status(), parentId, op.plannedStart(),
                        op.plannedEnd(), op.estimateHours(), current.actualHours(), current.progressPercent(),
                        op.isMilestone(), null));
                count++;
            } catch (ApiException e) {
                log.warn("Dropped task UPDATE for {}: {}", op.existingTaskId(), e.getMessage());
            }
        }
        return count;
    }

    private int applyReorders(UserId userId, UUID projectId, Map<String, UUID> idByKey,
            List<ReorderOperation> operations) {
        int count = 0;
        for (ReorderOperation op : operations) {
            UUID parentId = resolveRef(op.parentRef(), idByKey);
            List<UUID> orderedIds = new ArrayList<>(op.orderedRefs().size());
            boolean allResolved = true;
            for (String ref : op.orderedRefs()) {
                UUID id = resolveRef(ref, idByKey);
                if (id == null) {
                    allResolved = false;
                    break;
                }
                orderedIds.add(id);
            }
            if (!allResolved) {
                log.warn("Dropped reorder for parentRef={}: an entry didn't resolve", op.parentRef());
                continue;
            }
            try {
                taskService.reorder(userId, projectId, parentId, orderedIds);
                count++;
            } catch (ApiException e) {
                log.warn("Dropped reorder for parentRef={}: {}", op.parentRef(), e.getMessage());
            }
        }
        return count;
    }

    private int applyDependencyRemovals(UserId userId, List<DependencyOperation> operations) {
        int count = 0;
        for (DependencyOperation op : operations) {
            if (!"REMOVE".equals(op.op())) {
                continue;
            }
            try {
                dependencyService.delete(userId, op.existingDependencyId());
                count++;
            } catch (ApiException e) {
                log.warn("Dropped dependency REMOVE for {}: {}", op.existingDependencyId(), e.getMessage());
            }
        }
        return count;
    }

    private int applyDependencyAdditions(UserId userId, Map<String, UUID> idByKey,
            List<DependencyOperation> operations) {
        int count = 0;
        for (DependencyOperation op : operations) {
            if (!"ADD".equals(op.op())) {
                continue;
            }
            UUID predecessorId = resolveRef(op.predecessorRef(), idByKey);
            UUID successorId = resolveRef(op.successorRef(), idByKey);
            if (predecessorId == null || successorId == null) {
                log.warn("Dropped dependency ADD {} -> {}: unknown/excluded reference",
                        op.predecessorRef(), op.successorRef());
                continue;
            }
            try {
                dependencyService.create(userId, successorId,
                        new TaskDependencyService.CreateCommand(predecessorId, op.type(), op.lagDays()));
                count++;
            } catch (ApiException e) {
                log.warn("Dropped dependency ADD {} -> {}: {}", predecessorId, successorId, e.getMessage());
            }
        }
        return count;
    }

    /**
     * Resolves a reference string "existing-id-first" (D1 of the M9.5 plan):
     * a value that parses as a {@link UUID} is trusted as a real existing
     * task id; otherwise it's looked up as a same-command {@code key} for a
     * task just added by {@link #applyTaskAdditions} — {@code null} if
     * neither resolves.
     */
    private static UUID resolveRef(String ref, Map<String, UUID> idByKey) {
        if (ref == null) {
            return null;
        }
        try {
            return UUID.fromString(ref);
        } catch (IllegalArgumentException e) {
            return idByKey.get(ref);
        }
    }
}
