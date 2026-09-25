package com.kairon.assistant.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.llm.PlannedDependencyPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.PlannedDependency;
import com.kairon.projects.api.ProjectsApi.PlannedTask;
import com.kairon.projects.api.ProjectsApi.ProjectPlanCommand;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accept/dismiss for one {@code assistant_suggested_project}. Accept builds a
 * {@link ProjectPlanCommand} from the stored plan — cascading any caller-excluded
 * task key to its children first (D6) — and creates the whole project via
 * {@link ProjectsApi#createFromPlan}, never writing project rows itself
 * outside that one explicit user action (docs/milestones/M8.5-project-generation.md D3/D6).
 */
@Service
public class SuggestedProjectService {

    private static final Logger log = LoggerFactory.getLogger(SuggestedProjectService.class);

    private final AssistantSuggestedProjectRepository suggestedProjects;
    private final ProjectsApi projectsApi;
    private final ObjectMapper objectMapper;

    public SuggestedProjectService(AssistantSuggestedProjectRepository suggestedProjects, ProjectsApi projectsApi,
            ObjectMapper objectMapper) {
        this.suggestedProjects = suggestedProjects;
        this.projectsApi = projectsApi;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectView accept(UserId userId, UUID id, List<String> excludedTaskKeys) {
        AssistantSuggestedProject row = require(userId, id);
        requireProposed(row);
        PersistedProjectPlan plan = parse(row);
        Set<String> excluded = cascadeExclude(plan.tasks(), excludedTaskKeys == null ? List.of() : excludedTaskKeys);
        ProjectPlanCommand command = toCommand(plan, excluded);
        ProjectView created = projectsApi.createFromPlan(userId, command);
        row.accept(created.id());
        suggestedProjects.save(row);
        log.info("Accepted project plan {} userId={} -> project {}", id, userId.value(), created.id());
        return created;
    }

    @Transactional
    public AssistantSuggestedProjectView dismiss(UserId userId, UUID id) {
        AssistantSuggestedProject row = require(userId, id);
        requireProposed(row);
        row.dismiss();
        suggestedProjects.save(row);
        log.info("Dismissed project plan {} userId={}", id, userId.value());
        return AssistantMapper.toSuggestedProjectView(row, parse(row));
    }

    /** D6: excluding a task cascades to its children — one pass suffices given the ≤2-level cap. */
    private Set<String> cascadeExclude(List<PlannedTaskPayload> tasks, List<String> excludedTaskKeys) {
        Set<String> excluded = new HashSet<>(excludedTaskKeys);
        for (PlannedTaskPayload t : tasks) {
            if (t.parentKey() != null && excluded.contains(t.parentKey())) {
                excluded.add(t.key());
            }
        }
        return excluded;
    }

    private ProjectPlanCommand toCommand(PersistedProjectPlan plan, Set<String> excluded) {
        List<PlannedTask> tasks = new ArrayList<>();
        for (PlannedTaskPayload t : plan.tasks()) {
            if (!excluded.contains(t.key())) {
                tasks.add(new PlannedTask(t.key(), t.parentKey(), t.name(), t.description(), t.isMilestone(),
                        t.plannedStart(), t.plannedEnd(), t.estimateHours()));
            }
        }
        List<PlannedDependency> dependencies = new ArrayList<>();
        for (PlannedDependencyPayload d : plan.dependencies()) {
            if (!excluded.contains(d.predecessorKey()) && !excluded.contains(d.successorKey())) {
                dependencies.add(new PlannedDependency(d.predecessorKey(), d.successorKey(), d.type(), d.lagDays()));
            }
        }
        return new ProjectPlanCommand(plan.name(), plan.description(), plan.size(), plan.startDate(),
                plan.endDate(), tasks, dependencies);
    }

    private PersistedProjectPlan parse(AssistantSuggestedProject row) {
        return objectMapper.convertValue(row.getPlan(), PersistedProjectPlan.class);
    }

    private void requireProposed(AssistantSuggestedProject row) {
        if (row.isResolved()) {
            throw ApiException.conflict(
                    "This project plan was already " + row.getStatus().name().toLowerCase() + ".");
        }
    }

    private AssistantSuggestedProject require(UserId userId, UUID id) {
        return suggestedProjects.findByIdAndUserId(id, userId.value())
                .orElseThrow(() -> ApiException.notFound("Suggested project not found."));
    }
}
