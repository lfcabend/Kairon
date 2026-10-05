package com.kairon.assistant.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.llm.DependencyOperationPayload;
import com.kairon.assistant.llm.TaskOperationPayload;
import com.kairon.assistant.repo.AssistantSuggestedProjectEditRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.DependencyOperation;
import com.kairon.projects.api.ProjectsApi.ProjectEditCommand;
import com.kairon.projects.api.ProjectsApi.ProjectFieldChanges;
import com.kairon.projects.api.ProjectsApi.ReorderOperation;
import com.kairon.projects.api.ProjectsApi.TaskOperation;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accept/dismiss for one {@code assistant_suggested_project_edit}. Accept
 * builds a {@link ProjectEditCommand} from the stored diff — cascading any
 * caller-excluded new-task key to anything else in the diff that references
 * it (M9.5 D1, generalizing M8.5 D6's "excluding a task cascades to its
 * children") — and applies it to the one existing project via
 * {@link ProjectsApi#applyProjectEdit}, never writing project rows itself
 * outside that one explicit user action.
 */
@Service
public class SuggestedProjectEditService {

    private static final Logger log = LoggerFactory.getLogger(SuggestedProjectEditService.class);

    private final AssistantSuggestedProjectEditRepository suggestedProjectEdits;
    private final ProjectsApi projectsApi;
    private final ObjectMapper objectMapper;

    public SuggestedProjectEditService(AssistantSuggestedProjectEditRepository suggestedProjectEdits,
            ProjectsApi projectsApi, ObjectMapper objectMapper) {
        this.suggestedProjectEdits = suggestedProjectEdits;
        this.projectsApi = projectsApi;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectView accept(UserId userId, UUID id, List<String> excludedOperationKeys) {
        AssistantSuggestedProjectEdit row = require(userId, id);
        requireProposed(row);
        PersistedProjectEdit diff = parse(row);
        Set<String> excluded = excludedOperationKeys == null ? Set.of() : new HashSet<>(excludedOperationKeys);
        ProjectEditCommand command = toCommand(diff, excluded);
        ProjectView updated = projectsApi.applyProjectEdit(userId, row.getProjectId(), command);
        row.accept();
        suggestedProjectEdits.save(row);
        log.info("Accepted project edit {} userId={} projectId={}", id, userId.value(), row.getProjectId());
        return updated;
    }

    @Transactional
    public AssistantSuggestedProjectEditView dismiss(UserId userId, UUID id) {
        AssistantSuggestedProjectEdit row = require(userId, id);
        requireProposed(row);
        row.dismiss();
        suggestedProjectEdits.save(row);
        log.info("Dismissed project edit {} userId={}", id, userId.value());
        return AssistantMapper.toSuggestedProjectEditView(row, parse(row));
    }

    private ProjectEditCommand toCommand(PersistedProjectEdit diff, Set<String> excludedPositional) {
        List<TaskOperationPayload> tasks = diff.taskOperations();
        Set<String> excludedNewTaskKeys = new HashSet<>();
        for (int i = 0; i < tasks.size(); i++) {
            TaskOperationPayload t = tasks.get(i);
            if ("ADD".equals(t.op()) && excludedPositional.contains("task:" + i)) {
                excludedNewTaskKeys.add(t.key());
            }
        }

        List<TaskOperation> taskOps = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            TaskOperationPayload t = tasks.get(i);
            if (excludedPositional.contains("task:" + i) || isExcludedRef(t.parentRef(), excludedNewTaskKeys)) {
                continue;
            }
            UUID existingTaskId = parseUuidOrNull(t.existingTaskId());
            if (("UPDATE".equals(t.op()) || "REMOVE".equals(t.op())) && existingTaskId == null) {
                log.warn("Dropped task {} operation at index {}: existingTaskId didn't parse", t.op(), i);
                continue;
            }
            taskOps.add(new TaskOperation(t.op(), existingTaskId, t.key(), t.parentRef(), t.name(),
                    t.description(), t.isMilestone(), t.plannedStart(), t.plannedEnd(), t.estimateHours()));
        }

        List<DependencyOperationPayload> deps = diff.dependencyOperations();
        List<DependencyOperation> depOps = new ArrayList<>();
        for (int i = 0; i < deps.size(); i++) {
            DependencyOperationPayload d = deps.get(i);
            if (excludedPositional.contains("dependency:" + i)
                    || isExcludedRef(d.predecessorRef(), excludedNewTaskKeys)
                    || isExcludedRef(d.successorRef(), excludedNewTaskKeys)) {
                continue;
            }
            UUID existingDependencyId = parseUuidOrNull(d.existingDependencyId());
            if ("REMOVE".equals(d.op()) && existingDependencyId == null) {
                log.warn("Dropped dependency REMOVE at index {}: existingDependencyId didn't parse", i);
                continue;
            }
            depOps.add(new DependencyOperation(d.op(), existingDependencyId, d.predecessorRef(), d.successorRef(),
                    d.type(), d.lagDays()));
        }

        List<ReorderOperation> reorderOps = diff.reorderOperations().stream()
                .filter(r -> r.orderedRefs().stream().noneMatch(excludedNewTaskKeys::contains)
                        && !isExcludedRef(r.parentRef(), excludedNewTaskKeys))
                .map(r -> new ReorderOperation(r.parentRef(), r.orderedRefs()))
                .toList();

        ProjectFieldChanges projectChanges = null;
        if (diff.projectChanges() != null && !excludedPositional.contains("project")) {
            PersistedProjectEdit.PersistedProjectFieldChanges pc = diff.projectChanges();
            // pc.categoryId() (an existing match) wins; otherwise pc.categoryName() — if the
            // model proposed one with no match — is a new category name to create on accept
            // (mirrors PersistedProjectPlan's own convention, SuggestedProjectService#toCommand).
            String newCategoryName = pc.categoryId() == null ? pc.categoryName() : null;
            projectChanges = new ProjectFieldChanges(pc.name(), pc.description(), pc.size(), pc.startDate(),
                    pc.endDate(), pc.categoryId(), newCategoryName);
        }

        return new ProjectEditCommand(projectChanges, taskOps, depOps, reorderOps);
    }

    private static boolean isExcludedRef(String ref, Set<String> excludedNewTaskKeys) {
        return ref != null && excludedNewTaskKeys.contains(ref);
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PersistedProjectEdit parse(AssistantSuggestedProjectEdit row) {
        return objectMapper.convertValue(row.getDiff(), PersistedProjectEdit.class);
    }

    private void requireProposed(AssistantSuggestedProjectEdit row) {
        if (row.isResolved()) {
            throw ApiException.conflict(
                    "This project edit was already " + row.getStatus().name().toLowerCase() + ".");
        }
    }

    private AssistantSuggestedProjectEdit require(UserId userId, UUID id) {
        return suggestedProjectEdits.findByIdAndUserId(id, userId.value())
                .orElseThrow(() -> ApiException.notFound("Suggested project edit not found."));
    }
}
