package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.journal.api.JournalApi;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The MCP entry point's own service (D20) — five context-read / write-back
 * pairs mirroring {@link AssistantRunService}'s own five features, but never
 * calling {@link com.kairon.assistant.llm.AnthropicClient}: the reasoning
 * already happened inside the caller's own Claude Code session, outside this
 * instance entirely (docs/milestones/M10.5-mcp-server.md D20). Reuses
 * {@link AssistantAvailability} (D7 — the same per-feature opt-in gates the
 * Anthropic-API path enforces) and {@link AssistantSuggestionWriter} (D18 —
 * identical capping/category-resolution/clamping) so a Claude-Code-authored
 * suggestion is persisted through exactly the same rules.
 *
 * <p>Every write-back rebuilds its own context server-side (the same cheap,
 * deterministic read the matching {@code read*Context} method performs)
 * purely to populate {@code input_snapshot} truthfully — D13's own
 * "re-derive rather than trust a caller-supplied snapshot" reasoning,
 * generalized here to all five pairs rather than only {@code submitSummary}.
 * A write-back never draws from the per-user monthly token budget (D5 — it
 * always calls {@code run.succeed(0, 0)}) and is tagged
 * {@link AssistantRun#markMcp()} (D10). A write-back's {@code model} is
 * free-text, caller-reported, and never validated (D11) — unlike
 * {@link AssistantAvailability#resolveModel}, which only applies to the
 * Anthropic-API path's own model override.
 */
@Service
public class McpAssistantService {

    private static final Logger log = LoggerFactory.getLogger(McpAssistantService.class);
    private static final String DEFAULT_MODEL = "mcp";

    private final AssistantRunRepository runs;
    private final AssistantAvailability availability;
    private final AssistantSuggestionWriter writer;
    private final SuggestedProjectEditService suggestedProjectEditService;
    private final ProjectsApi projectsApi;
    private final JournalApi journal;
    private final TodoSuggestionContextBuilder todoContextBuilder;
    private final ProjectPlanContextBuilder projectPlanContextBuilder;
    private final ProjectEditContextBuilder projectEditContextBuilder;
    private final SummaryContextBuilder summaryContextBuilder;
    private final JournalReflectionContextBuilder journalReflectionContextBuilder;
    private final ObjectMapper objectMapper;

    public McpAssistantService(AssistantRunRepository runs, AssistantAvailability availability,
            AssistantSuggestionWriter writer, SuggestedProjectEditService suggestedProjectEditService,
            ProjectsApi projectsApi, JournalApi journal,
            TodoSuggestionContextBuilder todoContextBuilder, ProjectPlanContextBuilder projectPlanContextBuilder,
            ProjectEditContextBuilder projectEditContextBuilder, SummaryContextBuilder summaryContextBuilder,
            JournalReflectionContextBuilder journalReflectionContextBuilder, ObjectMapper objectMapper) {
        this.runs = runs;
        this.availability = availability;
        this.writer = writer;
        this.suggestedProjectEditService = suggestedProjectEditService;
        this.projectsApi = projectsApi;
        this.journal = journal;
        this.todoContextBuilder = todoContextBuilder;
        this.projectPlanContextBuilder = projectPlanContextBuilder;
        this.projectEditContextBuilder = projectEditContextBuilder;
        this.summaryContextBuilder = summaryContextBuilder;
        this.journalReflectionContextBuilder = journalReflectionContextBuilder;
        this.objectMapper = objectMapper;
    }

    // --- Todo suggestions --------------------------------------------------

    @Transactional(readOnly = true)
    public RenderedContext readTodoSuggestionContext(UserId userId, LocalDate day, Horizon horizon) {
        availability.requireTodoSuggestionsAvailable(userId);
        TodoSuggestionContextBuilder.Context context = todoContextBuilder.build(userId, day, horizon);
        return new RenderedContext(context.systemPrompt(), context.userContent());
    }

    @Transactional
    public AssistantRunView submitTodoSuggestions(UserId userId, LocalDate day, Horizon horizon, String model,
            List<SuggestedTaskPayload> suggestions) {
        availability.requireTodoSuggestionsAvailable(userId);
        LocalDate periodEnd = horizon == Horizon.WEEK ? day.plusDays(6) : day;

        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.TODO_SUGGESTION, day, periodEnd,
                resolveCallerModel(model));
        run.markMcp();
        runs.save(run);
        run.start(todoContextBuilder.build(userId, day, horizon).inputSnapshot());
        runs.save(run);

        List<AssistantSuggestedTask> tasks = writer.saveSuggestions(run, suggestions, day, periodEnd);
        run.succeed(0, 0);
        runs.save(run);
        log.info("Recorded MCP-submitted TODO_SUGGESTION run {} userId={} suggestions={}",
                run.getId(), userId.value(), tasks.size());
        return AssistantMapper.toRunView(run, tasks);
    }

    // --- Project plan -------------------------------------------------------

    @Transactional(readOnly = true)
    public RenderedContext readProjectPlanContext(UserId userId, String description, LocalDate startDate,
            LocalDate targetDeadline) {
        availability.requireProjectGenerationAvailable(userId);
        ProjectPlanContextBuilder.Context context = projectPlanContextBuilder.build(userId, description, startDate,
                targetDeadline);
        return new RenderedContext(context.systemPrompt(), context.userContent());
    }

    @Transactional
    public AssistantRunView submitProjectPlan(UserId userId, String description, LocalDate startDate,
            LocalDate targetDeadline, String model, ProjectPlanPayload plan) {
        availability.requireProjectGenerationAvailable(userId);

        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.PROJECT_GENERATION, startDate,
                targetDeadline, resolveCallerModel(model));
        run.markMcp();
        runs.save(run);
        ProjectPlanContextBuilder.Context context = projectPlanContextBuilder.build(userId, description, startDate,
                targetDeadline);
        run.start(context.inputSnapshot());
        runs.save(run);

        AssistantSuggestedProject saved = writer.saveSuggestedProject(run, plan, startDate, targetDeadline,
                context.categories());
        run.succeed(0, 0);
        runs.save(run);
        PersistedProjectPlan persisted = objectMapper.convertValue(saved.getPlan(), PersistedProjectPlan.class);
        log.info("Recorded MCP-submitted PROJECT_GENERATION run {} userId={} tasks={}",
                run.getId(), userId.value(), persisted.tasks().size());
        return AssistantMapper.toRunView(run, List.of(), AssistantMapper.toSuggestedProjectView(saved, persisted));
    }

    // --- Project edit --------------------------------------------------------

    @Transactional(readOnly = true)
    public RenderedContext readProjectEditContext(UserId userId, UUID projectId, String description) {
        availability.requireProjectEditingAvailable(userId);
        ProjectEditContextBuilder.Context context = projectEditContextBuilder.build(userId, projectId, description); // 404s if foreign (D14)
        return new RenderedContext(context.systemPrompt(), context.userContent());
    }

    @Transactional
    public AssistantRunView submitProjectEdit(UserId userId, UUID projectId, String description, String model,
            ProjectEditPayload diff) {
        availability.requireProjectEditingAvailable(userId);
        projectsApi.requireProject(userId, projectId); // re-checked (D14's own posture)

        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.PROJECT_EDIT, null, null,
                resolveCallerModel(model));
        run.markMcp();
        runs.save(run);
        ProjectEditContextBuilder.Context context = projectEditContextBuilder.build(userId, projectId, description);
        run.start(context.inputSnapshot());
        runs.save(run);

        AssistantSuggestedProjectEdit saved = writer.saveSuggestedEdit(run, projectId, diff, context.categories());
        run.succeed(0, 0);
        runs.save(run);
        PersistedProjectEdit persisted = objectMapper.convertValue(saved.getDiff(), PersistedProjectEdit.class);
        log.info("Recorded MCP-submitted PROJECT_EDIT run {} userId={} projectId={} taskOps={}",
                run.getId(), userId.value(), projectId, persisted.taskOperations().size());
        return AssistantMapper.toRunView(run, List.of(), null,
                AssistantMapper.toSuggestedProjectEditView(saved, persisted));
    }

    /**
     * Same as {@link #submitProjectEdit}, but applies the diff immediately
     * instead of leaving it {@code PROPOSED} for later review — the Claude
     * Code conversation that produced it *is* the review step. Reuses
     * {@link SuggestedProjectEditService#accept} so the apply goes through
     * exactly the same validation/cascade logic the web UI's own "Edit with
     * AI" accept button uses, immediately after persisting the same
     * {@code PROPOSED} audit row {@link #submitProjectEdit} would.
     */
    @Transactional
    public ProjectEditApplyResult applyProjectEdit(UserId userId, UUID projectId, String description, String model,
            ProjectEditPayload diff) {
        availability.requireProjectEditingAvailable(userId);
        projectsApi.requireProject(userId, projectId); // re-checked (D14's own posture)

        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.PROJECT_EDIT, null, null,
                resolveCallerModel(model));
        run.markMcp();
        runs.save(run);
        ProjectEditContextBuilder.Context context = projectEditContextBuilder.build(userId, projectId, description);
        run.start(context.inputSnapshot());
        runs.save(run);

        AssistantSuggestedProjectEdit saved = writer.saveSuggestedEdit(run, projectId, diff, context.categories());
        run.succeed(0, 0);
        runs.save(run);

        ProjectView updated = suggestedProjectEditService.accept(userId, saved.getId(), null);
        List<ProjectTaskView> tasks = projectsApi.tasksForProject(userId, projectId);
        log.info("Applied MCP-submitted PROJECT_EDIT run {} userId={} projectId={} (auto-accepted)",
                run.getId(), userId.value(), projectId);
        return new ProjectEditApplyResult(updated, tasks);
    }

    // --- Summary -------------------------------------------------------------

    @Transactional(readOnly = true)
    public RenderedContext readSummaryContext(UserId userId, SummaryPeriod period, LocalDate date) {
        availability.requireExecutionSummariesAvailable(userId);
        SummaryPeriod.Range range = period.resolve(date);
        SummaryContextBuilder.Context context = summaryContextBuilder.build(userId, period.kind(), range.start(),
                range.end());
        return new RenderedContext(context.systemPrompt(), context.userContent());
    }

    @Transactional
    public AssistantRunView submitSummary(UserId userId, SummaryPeriod period, LocalDate date, String model,
            String markdown) {
        availability.requireExecutionSummariesAvailable(userId);
        SummaryPeriod.Range range = period.resolve(date);

        AssistantRun run = AssistantRun.pending(userId.value(), period.kind(), range.start(), range.end(),
                resolveCallerModel(model));
        run.markMcp();
        runs.save(run);
        SummaryContextBuilder.Context context = summaryContextBuilder.build(userId, period.kind(), range.start(),
                range.end());
        run.start(context.inputSnapshot());
        // D13 — the stats table is always server-rendered, never model-authored, on
        // every entry point, not just the Anthropic-API one.
        String outputMarkdown = markdown + "\n\n" + context.renderStatsTable();
        run.succeed(outputMarkdown, 0, 0);
        runs.save(run);
        log.info("Recorded MCP-submitted {} run {} userId={} period=[{},{}]",
                period.kind(), run.getId(), userId.value(), range.start(), range.end());
        return AssistantMapper.toRunView(run, List.of());
    }

    // --- Journal reflection ----------------------------------------------------

    @Transactional(readOnly = true)
    public RenderedContext readJournalReflectionContext(UserId userId, LocalDate weekOf) {
        AssistantPreferencesView prefs = availability.requireJournalReflectionAvailable(userId);
        SummaryPeriod.Range week = SummaryPeriod.WEEK.resolve(weekOf);
        requireNonEmptyWeek(userId, week);
        JournalReflectionContextBuilder.Context context = journalReflectionContextBuilder.build(userId,
                week.start(), week.end(), prefs.tone());
        return new RenderedContext(context.systemPrompt(), context.userContent());
    }

    @Transactional
    public AssistantRunView submitJournalReflection(UserId userId, LocalDate weekOf, String model, String markdown) {
        AssistantPreferencesView prefs = availability.requireJournalReflectionAvailable(userId);
        SummaryPeriod.Range week = SummaryPeriod.WEEK.resolve(weekOf);
        requireNonEmptyWeek(userId, week); // re-checked (D14's own posture)

        AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.JOURNAL_REFLECTION, week.start(),
                week.end(), resolveCallerModel(model));
        run.markMcp();
        runs.save(run);
        JournalReflectionContextBuilder.Context context = journalReflectionContextBuilder.build(userId,
                week.start(), week.end(), prefs.tone());
        run.start(context.inputSnapshot());
        run.succeed(markdown, 0, 0); // no stats table on any entry point (M10 D11)
        runs.save(run);
        log.info("Recorded MCP-submitted JOURNAL_REFLECTION run {} userId={} week=[{},{}]",
                run.getId(), userId.value(), week.start(), week.end());
        return AssistantMapper.toRunView(run, List.of());
    }

    private void requireNonEmptyWeek(UserId userId, SummaryPeriod.Range week) {
        if (journal.range(userId, week.start(), week.end()).isEmpty()) {
            log.warn("MCP journal reflection refused: no entries userId={} week=[{},{}]",
                    userId.value(), week.start(), week.end());
            throw ApiException.unprocessable("No journal entries for that week — nothing to reflect on.");
        }
    }

    /** D11 — free-text, caller-reported, never validated against a known-models list. */
    private static String resolveCallerModel(String model) {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }
}
