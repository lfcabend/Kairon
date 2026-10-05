package com.kairon.assistant.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.domain.AssistantRunStatus;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.DependencyOperationPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.ReorderOperationPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.llm.TaskOperationPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.assistant.repo.AssistantSuggestedProjectEditRepository;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;
import com.kairon.journal.api.JournalApi;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.ProjectCategorySummary;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the {@code assistant_run} lifecycle. {@link #requestTodoSuggestions}
 * runs synchronously in-request (M8 D7): create {@code PENDING}, gather
 * context and flip {@code RUNNING}, call the Anthropic client, persist
 * {@code SUCCEEDED}/{@code FAILED} — all before returning.
 */
@Service
public class AssistantRunService {

    private static final Logger log = LoggerFactory.getLogger(AssistantRunService.class);

    // Validated model overrides (M8 §4.4 conversation) — an invalid per-user
    // override falls back to the instance default rather than being sent to the
    // SDK as-is and failing the call.
    private static final Set<String> KNOWN_MODELS =
            Set.of("claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5");

    private final AssistantRunRepository runs;
    private final AssistantSuggestedTaskRepository suggestedTasks;
    private final AssistantSuggestedProjectRepository suggestedProjects;
    private final AssistantSuggestedProjectEditRepository suggestedProjectEdits;
    private final AssistantProperties properties;
    private final UserAccountApi accounts;
    private final ProjectsApi projectsApi;
    private final TodoSuggestionContextBuilder contextBuilder;
    private final ProjectPlanContextBuilder projectPlanContextBuilder;
    private final ProjectEditContextBuilder projectEditContextBuilder;
    private final JournalApi journal;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AssistantRunService(AssistantRunRepository runs, AssistantSuggestedTaskRepository suggestedTasks,
            AssistantSuggestedProjectRepository suggestedProjects,
            AssistantSuggestedProjectEditRepository suggestedProjectEdits, AssistantProperties properties,
            UserAccountApi accounts, ProjectsApi projectsApi, TodoSuggestionContextBuilder contextBuilder,
            ProjectPlanContextBuilder projectPlanContextBuilder, ProjectEditContextBuilder projectEditContextBuilder,
            JournalApi journal, AnthropicClient anthropicClient, ObjectMapper objectMapper, Clock clock) {
        this.runs = runs;
        this.suggestedTasks = suggestedTasks;
        this.suggestedProjects = suggestedProjects;
        this.suggestedProjectEdits = suggestedProjectEdits;
        this.properties = properties;
        this.accounts = accounts;
        this.projectsApi = projectsApi;
        this.contextBuilder = contextBuilder;
        this.projectPlanContextBuilder = projectPlanContextBuilder;
        this.projectEditContextBuilder = projectEditContextBuilder;
        this.journal = journal;
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AssistantRunView requestTodoSuggestions(UserId userId, LocalDate day, Horizon horizon) {
        AssistantPreferencesView prefs = requireAvailable(userId);
        requireWithinBudget(userId);

        LocalDate periodEnd = horizon == Horizon.WEEK ? day.plusDays(6) : day;
        String model = resolveModel(prefs);
        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.TODO_SUGGESTION, day,
                periodEnd, model);
        runs.save(run);

        TodoSuggestionContextBuilder.Context context = contextBuilder.build(userId, day, horizon);
        run.start(context.inputSnapshot());
        runs.save(run);

        try {
            AnthropicClient.TodoSuggestionsResult result = anthropicClient.suggestTodos(
                    new AnthropicClient.TodoSuggestionRequest(context.systemPrompt(), context.userContent(),
                            model, properties.todoSuggestions().effort()));

            List<AssistantSuggestedTask> tasks = saveSuggestions(run, result.suggestions(), day, periodEnd);
            run.succeed(result.inputTokens(), result.outputTokens());
            runs.save(run);
            log.info("Todo-suggestion run {} succeeded userId={} suggestions={} tokens={}+{}",
                    run.getId(), userId.value(), tasks.size(), result.inputTokens(), result.outputTokens());
            return AssistantMapper.toRunView(run, tasks);
        } catch (com.kairon.assistant.app.AssistantUpstreamException e) {
            run.fail(e.sanitizedDetail());
            runs.save(run);
            log.warn("Todo-suggestion run {} failed userId={} retryable={}",
                    run.getId(), userId.value(), e.retryable());
            throw e.toApiException();
        }
    }

    @Transactional
    public AssistantRunView requestProjectPlan(UserId userId, String description, LocalDate startDate,
            LocalDate targetDeadline) {
        AssistantPreferencesView prefs = requireProjectGenerationAvailable(userId);
        requireWithinBudget(userId);

        String model = resolveModel(prefs);
        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.PROJECT_GENERATION, startDate,
                targetDeadline, model);
        runs.save(run);

        ProjectPlanContextBuilder.Context context = projectPlanContextBuilder.build(userId, description, startDate,
                targetDeadline);
        run.start(context.inputSnapshot());
        runs.save(run);

        try {
            AnthropicClient.ProjectPlanResult result = anthropicClient.generateProjectPlan(
                    new AnthropicClient.ProjectPlanRequest(context.systemPrompt(), context.userContent(), model));

            AssistantSuggestedProject saved = saveSuggestedProject(run, result.plan(), startDate, targetDeadline,
                    context.categories());
            run.succeed(result.inputTokens(), result.outputTokens());
            runs.save(run);
            PersistedProjectPlan plan = objectMapper.convertValue(saved.getPlan(), PersistedProjectPlan.class);
            log.info("Project-plan run {} succeeded userId={} tasks={} tokens={}+{}",
                    run.getId(), userId.value(), plan.tasks().size(), result.inputTokens(), result.outputTokens());
            return AssistantMapper.toRunView(run, List.of(), AssistantMapper.toSuggestedProjectView(saved, plan));
        } catch (AssistantUpstreamException e) {
            run.fail(e.sanitizedDetail());
            runs.save(run);
            log.warn("Project-plan run {} failed userId={} retryable={}",
                    run.getId(), userId.value(), e.retryable());
            throw e.toApiException();
        }
    }

    @Transactional
    public AssistantRunView requestProjectEdit(UserId userId, UUID projectId, String description) {
        AssistantPreferencesView prefs = requireProjectEditingAvailable(userId);
        projectsApi.requireProject(userId, projectId); // 404 fast, before any run row (D10)
        requireWithinBudget(userId);

        String model = resolveModel(prefs);
        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.PROJECT_EDIT, null, null, model);
        runs.save(run);

        ProjectEditContextBuilder.Context context = projectEditContextBuilder.build(userId, projectId, description);
        run.start(context.inputSnapshot());
        runs.save(run);

        try {
            AnthropicClient.ProjectEditResult result = anthropicClient.generateProjectEdit(
                    new AnthropicClient.ProjectEditRequest(context.systemPrompt(), context.userContent(), model));

            AssistantSuggestedProjectEdit saved = saveSuggestedEdit(run, projectId, result.diff(),
                    context.categories());
            run.succeed(result.inputTokens(), result.outputTokens());
            runs.save(run);
            PersistedProjectEdit persisted = objectMapper.convertValue(saved.getDiff(), PersistedProjectEdit.class);
            log.info("Project-edit run {} succeeded userId={} projectId={} taskOps={} tokens={}+{}",
                    run.getId(), userId.value(), projectId, persisted.taskOperations().size(),
                    result.inputTokens(), result.outputTokens());
            return AssistantMapper.toRunView(run, List.of(), null,
                    AssistantMapper.toSuggestedProjectEditView(saved, persisted));
        } catch (AssistantUpstreamException e) {
            run.fail(e.sanitizedDetail());
            runs.save(run);
            log.warn("Project-edit run {} failed userId={} retryable={}",
                    run.getId(), userId.value(), e.retryable());
            throw e.toApiException();
        }
    }

    /**
     * Queues a {@code WEEKLY_SUMMARY}/{@code MONTHLY_SUMMARY} run and returns
     * immediately — the caller (the controller, or {@code SummaryScheduler})
     * dispatches the actual generation only after this method's transaction has
     * committed (M9 D2).
     */
    @Transactional
    public AssistantRunView requestSummary(UserId userId, SummaryPeriod period, LocalDate date) {
        requireExecutionSummariesAvailable(userId);
        requireWithinBudget(userId);
        requireNoRunInFlight(userId, Set.of(AssistantRunKind.WEEKLY_SUMMARY, AssistantRunKind.MONTHLY_SUMMARY));

        SummaryPeriod.Range range = period.resolve(date);
        String model = resolveModel(accounts.assistantPreferences(userId));
        AssistantRun run = AssistantRun.pending(userId.value(), period.kind(), range.start(), range.end(), model);
        runs.save(run);
        log.info("Queued {} run {} userId={} period=[{},{}]",
                period.kind(), run.getId(), userId.value(), range.start(), range.end());
        return AssistantMapper.toRunView(run, List.of());
    }

    /**
     * Queues a {@code JOURNAL_REFLECTION} run and returns immediately — same
     * async/poll shape as {@link #requestSummary} (M10 D3). Refuses up front
     * (D8) if the week has no journal entries at all, before any run row is
     * created or any token spent.
     */
    @Transactional
    public AssistantRunView requestJournalReflection(UserId userId, LocalDate weekOf) {
        requireJournalReflectionAvailable(userId);
        requireWithinBudget(userId);
        requireNoRunInFlight(userId, Set.of(AssistantRunKind.JOURNAL_REFLECTION));

        SummaryPeriod.Range week = SummaryPeriod.WEEK.resolve(weekOf); // D6 — date math only
        if (journal.range(userId, week.start(), week.end()).isEmpty()) {
            log.warn("Journal reflection refused: no entries userId={} week=[{},{}]",
                    userId.value(), week.start(), week.end());
            throw ApiException.unprocessable("No journal entries for that week — nothing to reflect on.");
        }

        String model = resolveModel(accounts.assistantPreferences(userId));
        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.JOURNAL_REFLECTION, week.start(),
                week.end(), model);
        runs.save(run);
        log.info("Queued JOURNAL_REFLECTION run {} userId={} week=[{},{}]",
                run.getId(), userId.value(), week.start(), week.end());
        return AssistantMapper.toRunView(run, List.of());
    }

    /** Paginated run history (any kind), most recent first (M9 D12). */
    @Transactional(readOnly = true)
    public AssistantRunPage list(UserId userId, List<String> kind, LocalDate from, LocalDate to, int page,
            int pageSize) {
        boolean hasKinds = kind != null && !kind.isEmpty();
        List<AssistantRunKind> kinds = hasKinds ? kind.stream().map(AssistantRunService::parseKind).toList()
                : List.of();
        boolean hasFrom = from != null;
        Instant fromInstant = hasFrom ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        boolean hasTo = to != null;
        Instant toInstant = hasTo ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.now(clock);
        Page<AssistantRun> result = runs.findPage(userId.value(), hasKinds, kinds, hasFrom, fromInstant, hasTo,
                toInstant, PageRequest.of(page, pageSize));
        List<AssistantRunView> views = result.getContent().stream().map(AssistantMapper::toRunListView).toList();
        log.debug("Listed {} assistant run(s) userId={} kind={} range {}..{}",
                views.size(), userId.value(), kind, from, to);
        return new AssistantRunPage(views, result.getNumber(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AssistantRunView get(UserId userId, UUID id) {
        AssistantRun run = require(userId, id);
        if (run.getKind() == AssistantRunKind.PROJECT_GENERATION) {
            AssistantSuggestedProjectView suggestedProject = suggestedProjects.findByRunId(run.getId())
                    .map(row -> AssistantMapper.toSuggestedProjectView(row,
                            objectMapper.convertValue(row.getPlan(), PersistedProjectPlan.class)))
                    .orElse(null);
            return AssistantMapper.toRunView(run, List.of(), suggestedProject);
        }
        if (run.getKind() == AssistantRunKind.PROJECT_EDIT) {
            AssistantSuggestedProjectEditView suggestedProjectEdit = suggestedProjectEdits.findByRunId(run.getId())
                    .map(row -> AssistantMapper.toSuggestedProjectEditView(row,
                            objectMapper.convertValue(row.getDiff(), PersistedProjectEdit.class)))
                    .orElse(null);
            return AssistantMapper.toRunView(run, List.of(), null, suggestedProjectEdit);
        }
        List<AssistantSuggestedTask> tasks = run.getKind() == AssistantRunKind.TODO_SUGGESTION
                ? suggestedTasks.findByRunIdOrderByPositionAsc(run.getId())
                : List.of();
        return AssistantMapper.toRunView(run, tasks);
    }

    @Transactional
    public void delete(UserId userId, UUID id) {
        AssistantRun run = require(userId, id);
        runs.delete(run);
        log.info("Deleted assistant run {} userId={}", id, userId.value());
    }

    private List<AssistantSuggestedTask> saveSuggestions(AssistantRun run, List<SuggestedTaskPayload> payloads,
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

    private AssistantSuggestedProject saveSuggestedProject(AssistantRun run, ProjectPlanPayload payload,
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

    private AssistantSuggestedProjectEdit saveSuggestedEdit(AssistantRun run, UUID projectId,
            ProjectEditPayload payload, List<ProjectCategorySummary> categories) {
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
    private CategoryResolution resolveCategory(String categoryName,
            List<ProjectCategorySummary> categories) {
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
     * Every downstream root/child check (ProjectPlanImportService, the review
     * UI, SuggestedProjectService's cascade-exclude) treats {@code parentKey ==
     * null} as "top-level", so left un-normalized this silently flattens the
     * model's entire ≤2-level tree into one flat list of top-level tasks —
     * normalizing here, once, at the earliest point the payload is consumed,
     * means every downstream null-check is trustworthy.
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

    private AssistantPreferencesView requireAvailable(UserId userId) {
        if (!properties.available()) {
            log.warn("Assistant unavailable (master switch off or no API key) userId={}", userId.value());
            throw ApiException.forbidden("Assistant features are not enabled on this instance.");
        }
        AssistantPreferencesView prefs = accounts.assistantPreferences(userId);
        if (!prefs.todoSuggestionsEnabled()) {
            log.warn("Todo suggestions not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled todo suggestions in Settings.");
        }
        return prefs;
    }

    private AssistantPreferencesView requireProjectGenerationAvailable(UserId userId) {
        if (!properties.available()) {
            log.warn("Assistant unavailable (master switch off or no API key) userId={}", userId.value());
            throw ApiException.forbidden("Assistant features are not enabled on this instance.");
        }
        AssistantPreferencesView prefs = accounts.assistantPreferences(userId);
        if (!prefs.projectGenerationEnabled()) {
            log.warn("Project generation not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled project generation in Settings.");
        }
        return prefs;
    }

    private AssistantPreferencesView requireProjectEditingAvailable(UserId userId) {
        if (!properties.available()) {
            log.warn("Assistant unavailable (master switch off or no API key) userId={}", userId.value());
            throw ApiException.forbidden("Assistant features are not enabled on this instance.");
        }
        AssistantPreferencesView prefs = accounts.assistantPreferences(userId);
        if (!prefs.projectEditingEnabled()) {
            log.warn("Project editing not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled project editing in Settings.");
        }
        return prefs;
    }

    private AssistantPreferencesView requireExecutionSummariesAvailable(UserId userId) {
        if (!properties.available()) {
            log.warn("Assistant unavailable (master switch off or no API key) userId={}", userId.value());
            throw ApiException.forbidden("Assistant features are not enabled on this instance.");
        }
        AssistantPreferencesView prefs = accounts.assistantPreferences(userId);
        if (!prefs.executionSummariesEnabled()) {
            log.warn("Execution summaries not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled execution summaries in Settings.");
        }
        return prefs;
    }

    private AssistantPreferencesView requireJournalReflectionAvailable(UserId userId) {
        if (!properties.available()) {
            log.warn("Assistant unavailable (master switch off or no API key) userId={}", userId.value());
            throw ApiException.forbidden("Assistant features are not enabled on this instance.");
        }
        AssistantPreferencesView prefs = accounts.assistantPreferences(userId);
        if (!prefs.journalReflectionEnabled()) {
            log.warn("Journal reflection not opted into userId={}", userId.value());
            throw ApiException.forbidden("You haven't enabled journal reflection in Settings.");
        }
        return prefs;
    }

    /**
     * At most one in-flight (PENDING/RUNNING) run per user across the given
     * kinds (M10 D5, generalized from M9's hardcoded two-summary-kinds check).
     * The summary call site passes {@code {WEEKLY_SUMMARY, MONTHLY_SUMMARY}}
     * (unchanged behavior); {@code requestJournalReflection} passes its own
     * single kind — a reflection run never blocks, or is blocked by, a summary
     * run, since they're different reports a user may reasonably want at once.
     */
    private void requireNoRunInFlight(UserId userId, Set<AssistantRunKind> kinds) {
        boolean inFlight = runs.existsByUserIdAndKindInAndStatusIn(userId.value(), List.copyOf(kinds),
                List.of(AssistantRunStatus.PENDING, AssistantRunStatus.RUNNING));
        if (inFlight) {
            log.warn("Run rejected: a {} run is already in flight userId={}", kinds, userId.value());
            throw ApiException.conflict("A run of this kind is already in progress. Wait for it to finish.");
        }
    }

    private static AssistantRunKind parseKind(String raw) {
        try {
            return AssistantRunKind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown run kind: " + raw);
        }
    }

    private void requireWithinBudget(UserId userId) {
        Instant monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long used = runs.sumTokensSince(userId.value(), monthStart);
        if (used >= properties.monthlyTokenBudgetPerUser()) {
            log.warn("Monthly assistant token budget exceeded userId={} used={} budget={}",
                    userId.value(), used, properties.monthlyTokenBudgetPerUser());
            throw ApiException.forbidden("Monthly assistant usage budget reached. Try again next month.");
        }
    }

    private String resolveModel(AssistantPreferencesView prefs) {
        if (prefs.modelOverride() != null && KNOWN_MODELS.contains(prefs.modelOverride())) {
            return prefs.modelOverride();
        }
        if (prefs.modelOverride() != null) {
            log.warn("Ignoring unknown modelOverride '{}', falling back to instance default", prefs.modelOverride());
        }
        return properties.model();
    }

    private AssistantRun require(UserId userId, UUID id) {
        return runs.findByIdAndUserId(id, userId.value())
                .orElseThrow(() -> ApiException.notFound("Assistant run not found."));
    }
}
