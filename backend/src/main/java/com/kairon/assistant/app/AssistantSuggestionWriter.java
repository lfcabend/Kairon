package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.DependencyOperationPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.ReorderOperationPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.llm.TaskOperationPayload;
import com.kairon.assistant.repo.AssistantSuggestedProjectEditRepository;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.projects.api.ProjectsApi.ProjectCategorySummary;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The suggestion-persisting half of {@link AssistantRunService}, extracted
 * (D19) so {@link McpAssistantService} writes back through exactly the same
 * capping/category-resolution/clamping rules as the Anthropic-API path
 * (docs/milestones/M10.5-mcp-server.md D18) rather than a second copy of
 * them. Every method here assumes {@code run} already exists (saved by the
 * caller) and only persists the suggestion row(s) alongside it.
 */
@Component
class AssistantSuggestionWriter {

    private static final Logger log = LoggerFactory.getLogger(AssistantSuggestionWriter.class);

    private final AssistantProperties properties;
    private final AssistantSuggestedTaskRepository suggestedTasks;
    private final AssistantSuggestedProjectRepository suggestedProjects;
    private final AssistantSuggestedProjectEditRepository suggestedProjectEdits;
    private final ObjectMapper objectMapper;

    AssistantSuggestionWriter(AssistantProperties properties, AssistantSuggestedTaskRepository suggestedTasks,
            AssistantSuggestedProjectRepository suggestedProjects,
            AssistantSuggestedProjectEditRepository suggestedProjectEdits, ObjectMapper objectMapper) {
        this.properties = properties;
        this.suggestedTasks = suggestedTasks;
        this.suggestedProjects = suggestedProjects;
        this.suggestedProjectEdits = suggestedProjectEdits;
        this.objectMapper = objectMapper;
    }

    List<AssistantSuggestedTask> saveSuggestions(AssistantRun run, List<SuggestedTaskPayload> payloads,
            LocalDate from, LocalDate to) {
        int max = properties.todoSuggestions().maxSuggestions();
        List<SuggestedTaskPayload> capped = payloads.size() > max ? payloads.subList(0, max) : payloads;
        if (payloads.size() > max) {
            log.warn("Run {} returned {} suggestions, capped to {}", run.getId(), payloads.size(), max);
        }
        List<AssistantSuggestedTask> result = new ArrayList<>();
        int position = 0;
        for (SuggestedTaskPayload p : capped) {
            LocalDate suggestedForDay = clampToRange(run.getId(), p.suggestedForDay(), from, to);
            AssistantSuggestedTask task = AssistantSuggestedTask.propose(run.getId(), run.getUserId(),
                    p.title(), p.notes(), p.rationale(), suggestedForDay, p.estimateMinutes(),
                    p.sourceProjectTaskId(), position++);
            suggestedTasks.save(task);
            result.add(task);
        }
        return result;
    }

    AssistantSuggestedProject saveSuggestedProject(AssistantRun run, ProjectPlanPayload payload,
            LocalDate startDate, LocalDate endDate, List<ProjectCategorySummary> categories) {
        int max = properties.projectPlan().maxTasks();
        List<PlannedTaskPayload> tasks = normalizeParentKeys(payload.tasks());
        if (tasks.size() > max) {
            log.warn("Run {} plan returned {} tasks, capped to {}", run.getId(), tasks.size(), max);
            tasks = tasks.subList(0, max);
        }
        CategoryResolution category = resolveCategory(payload.categoryName(), categories);
        PersistedProjectPlan persisted = new PersistedProjectPlan(payload.name(), payload.description(),
                payload.size(), startDate, endDate, category.categoryId(), category.categoryName(), tasks,
                payload.dependencies());
        @SuppressWarnings("unchecked")
        Map<String, Object> planMap = objectMapper.convertValue(persisted, Map.class);
        AssistantSuggestedProject saved = AssistantSuggestedProject.propose(run.getId(), run.getUserId(), planMap);
        suggestedProjects.save(saved);
        return saved;
    }

    AssistantSuggestedProjectEdit saveSuggestedEdit(AssistantRun run, UUID projectId, ProjectEditPayload payload,
            List<ProjectCategorySummary> categories) {
        ProjectEditPayload capped = capProjectEditOperations(run.getId(), payload);
        PersistedProjectEdit.PersistedProjectFieldChanges projectChanges = null;
        if (capped.projectChanges() != null) {
            CategoryResolution category = resolveCategory(capped.projectChanges().categoryName(), categories);
            projectChanges = new PersistedProjectEdit.PersistedProjectFieldChanges(
                    capped.projectChanges().name(), capped.projectChanges().description(),
                    capped.projectChanges().size(), capped.projectChanges().startDate(),
                    capped.projectChanges().endDate(), category.categoryId(), category.categoryName());
        }
        PersistedProjectEdit persisted = new PersistedProjectEdit(projectChanges, capped.taskOperations(),
                capped.dependencyOperations(), capped.reorderOperations());
        @SuppressWarnings("unchecked")
        Map<String, Object> diffMap = objectMapper.convertValue(persisted, Map.class);
        AssistantSuggestedProjectEdit saved = AssistantSuggestedProjectEdit.propose(run.getId(), run.getUserId(),
                projectId, diffMap);
        suggestedProjectEdits.save(saved);
        return saved;
    }

    /**
     * Caps the *total* operation count (D9) — simpler than apportioning the
     * budget evenly across three different kinds of operation: task
     * operations are truncated first (the usual bulk of a diff), then
     * dependency operations against whatever budget remains, and reorder
     * operations are dropped entirely if nothing is left.
     */
    private ProjectEditPayload capProjectEditOperations(UUID runId, ProjectEditPayload payload) {
        int max = properties.projectEdit().maxOperations();
        List<TaskOperationPayload> tasks = payload.taskOperations();
        List<DependencyOperationPayload> dependencies = payload.dependencyOperations();
        List<ReorderOperationPayload> reorders = payload.reorderOperations();
        int total = tasks.size() + dependencies.size() + reorders.size();
        if (total <= max) {
            return payload;
        }
        log.warn("Run {} edit diff returned {} operation(s), capping to {}", runId, total, max);
        int budget = max;
        if (tasks.size() > budget) {
            tasks = tasks.subList(0, Math.max(budget, 0));
        }
        budget -= tasks.size();
        if (dependencies.size() > Math.max(budget, 0)) {
            dependencies = dependencies.subList(0, Math.max(budget, 0));
        }
        budget -= dependencies.size();
        if (budget <= 0) {
            reorders = List.of();
        }
        return new ProjectEditPayload(payload.projectChanges(), tasks, dependencies, reorders);
    }

    private record CategoryResolution(UUID categoryId, String categoryName) {
    }

    /**
     * Case-insensitive exact match against the categories passed to the model
     * in {@link ProjectPlanContextBuilder#build}. A match carries the existing
     * category's real id and its own stored name (for display); no match
     * carries just the model's proposed name, to be created on accept.
     */
    private CategoryResolution resolveCategory(String categoryName, List<ProjectCategorySummary> categories) {
        if (categoryName == null || categoryName.isBlank()) {
            return new CategoryResolution(null, null);
        }
        String trimmed = categoryName.trim();
        return categories.stream()
                .filter(c -> c.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .map(c -> new CategoryResolution(c.id(), c.name()))
                .orElse(new CategoryResolution(null, trimmed));
    }

    /**
     * The model reliably sends {@code ""} rather than omitting {@code parentKey}
     * (or sending JSON {@code null}) for a genuinely top-level task — observed
     * against the real API, not just a theoretical structured-output quirk.
     * Every downstream root/child check treats {@code parentKey == null} as
     * "top-level", so left un-normalized this silently flattens the model's
     * entire ≤2-level tree into one flat list of top-level tasks.
     */
    private List<PlannedTaskPayload> normalizeParentKeys(List<PlannedTaskPayload> tasks) {
        return tasks.stream()
                .map(t -> t.parentKey() != null && t.parentKey().isBlank()
                        ? new PlannedTaskPayload(t.key(), null, t.name(), t.description(), t.isMilestone(),
                                t.plannedStart(), t.plannedEnd(), t.estimateHours())
                        : t)
                .toList();
    }

    private LocalDate clampToRange(UUID runId, LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return from;
        }
        LocalDate clamped = date.isBefore(from) ? from : date.isAfter(to) ? to : date;
        if (!clamped.equals(date)) {
            log.warn("Run {} suggestedForDay {} outside [{}, {}], clamped to {}", runId, date, from, to, clamped);
        }
        return clamped;
    }
}
