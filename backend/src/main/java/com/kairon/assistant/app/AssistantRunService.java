package com.kairon.assistant.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.domain.AssistantRunStatus;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.AnthropicClient;
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
 * {@code SUCCEEDED}/{@code FAILED} — all before returning. The per-feature
 * opt-in gates and the suggestion-persisting logic live in
 * {@link AssistantAvailability}/{@link AssistantSuggestionWriter} (M10.5
 * D19), shared with {@link McpAssistantService}'s own zero-Anthropic-call
 * entry point.
 */
@Service
public class AssistantRunService {

    private static final Logger log = LoggerFactory.getLogger(AssistantRunService.class);

    private final AssistantRunRepository runs;
    private final AssistantSuggestedTaskRepository suggestedTasks;
    private final AssistantSuggestedProjectRepository suggestedProjects;
    private final AssistantSuggestedProjectEditRepository suggestedProjectEdits;
    private final AssistantProperties properties;
    private final AssistantAvailability availability;
    private final AssistantSuggestionWriter writer;
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
            AssistantAvailability availability, AssistantSuggestionWriter writer, UserAccountApi accounts,
            ProjectsApi projectsApi, TodoSuggestionContextBuilder contextBuilder,
            ProjectPlanContextBuilder projectPlanContextBuilder, ProjectEditContextBuilder projectEditContextBuilder,
            JournalApi journal, AnthropicClient anthropicClient, ObjectMapper objectMapper, Clock clock) {
        this.runs = runs;
        this.suggestedTasks = suggestedTasks;
        this.suggestedProjects = suggestedProjects;
        this.suggestedProjectEdits = suggestedProjectEdits;
        this.properties = properties;
        this.availability = availability;
        this.writer = writer;
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
        AssistantPreferencesView prefs = availability.requireTodoSuggestionsAvailable(userId);
        requireWithinBudget(userId);

        LocalDate periodEnd = horizon == Horizon.WEEK ? day.plusDays(6) : day;
        String model = availability.resolveModel(prefs);
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

            List<AssistantSuggestedTask> tasks = writer.saveSuggestions(run, result.suggestions(), day, periodEnd);
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
        AssistantPreferencesView prefs = availability.requireProjectGenerationAvailable(userId);
        requireWithinBudget(userId);

        String model = availability.resolveModel(prefs);
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

            AssistantSuggestedProject saved = writer.saveSuggestedProject(run, result.plan(), startDate,
                    targetDeadline, context.categories());
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
        AssistantPreferencesView prefs = availability.requireProjectEditingAvailable(userId);
        projectsApi.requireProject(userId, projectId); // 404 fast, before any run row (D10)
        requireWithinBudget(userId);

        String model = availability.resolveModel(prefs);
        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.PROJECT_EDIT, null, null, model);
        runs.save(run);

        ProjectEditContextBuilder.Context context = projectEditContextBuilder.build(userId, projectId, description);
        run.start(context.inputSnapshot());
        runs.save(run);

        try {
            AnthropicClient.ProjectEditResult result = anthropicClient.generateProjectEdit(
                    new AnthropicClient.ProjectEditRequest(context.systemPrompt(), context.userContent(), model));

            AssistantSuggestedProjectEdit saved = writer.saveSuggestedEdit(run, projectId, result.diff(),
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
        availability.requireExecutionSummariesAvailable(userId);
        requireWithinBudget(userId);
        requireNoRunInFlight(userId, Set.of(AssistantRunKind.WEEKLY_SUMMARY, AssistantRunKind.MONTHLY_SUMMARY));

        SummaryPeriod.Range range = period.resolve(date);
        String model = availability.resolveModel(accounts.assistantPreferences(userId));
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
        availability.requireJournalReflectionAvailable(userId);
        requireWithinBudget(userId);
        requireNoRunInFlight(userId, Set.of(AssistantRunKind.JOURNAL_REFLECTION));

        SummaryPeriod.Range week = SummaryPeriod.WEEK.resolve(weekOf); // D6 — date math only
        if (journal.range(userId, week.start(), week.end()).isEmpty()) {
            log.warn("Journal reflection refused: no entries userId={} week=[{},{}]",
                    userId.value(), week.start(), week.end());
            throw ApiException.unprocessable("No journal entries for that week — nothing to reflect on.");
        }

        String model = availability.resolveModel(accounts.assistantPreferences(userId));
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

    private AssistantRun require(UserId userId, UUID id) {
        return runs.findByIdAndUserId(id, userId.value())
                .orElseThrow(() -> ApiException.notFound("Assistant run not found."));
    }
}
