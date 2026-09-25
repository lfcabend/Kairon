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
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final AssistantProperties properties;
    private final UserAccountApi accounts;
    private final TodoSuggestionContextBuilder contextBuilder;
    private final ProjectPlanContextBuilder projectPlanContextBuilder;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AssistantRunService(AssistantRunRepository runs, AssistantSuggestedTaskRepository suggestedTasks,
            AssistantSuggestedProjectRepository suggestedProjects, AssistantProperties properties,
            UserAccountApi accounts, TodoSuggestionContextBuilder contextBuilder,
            ProjectPlanContextBuilder projectPlanContextBuilder, AnthropicClient anthropicClient,
            ObjectMapper objectMapper, Clock clock) {
        this.runs = runs;
        this.suggestedTasks = suggestedTasks;
        this.suggestedProjects = suggestedProjects;
        this.properties = properties;
        this.accounts = accounts;
        this.contextBuilder = contextBuilder;
        this.projectPlanContextBuilder = projectPlanContextBuilder;
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

        ProjectPlanContextBuilder.Context context = projectPlanContextBuilder.build(description, startDate,
                targetDeadline);
        run.start(context.inputSnapshot());
        runs.save(run);

        try {
            AnthropicClient.ProjectPlanResult result = anthropicClient.generateProjectPlan(
                    new AnthropicClient.ProjectPlanRequest(context.systemPrompt(), context.userContent(), model));

            AssistantSuggestedProject saved = saveSuggestedProject(run, result.plan(), startDate, targetDeadline);
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
            LocalDate startDate, LocalDate endDate) {
        int max = properties.projectPlan().maxTasks();
        List<PlannedTaskPayload> tasks = payload.tasks();
        if (tasks.size() > max) {
            log.warn("Run {} plan returned {} tasks, capped to {}", run.getId(), tasks.size(), max);
            tasks = tasks.subList(0, max);
        }
        PersistedProjectPlan persisted = new PersistedProjectPlan(payload.name(), payload.description(),
                payload.size(), startDate, endDate, tasks, payload.dependencies());
        @SuppressWarnings("unchecked")
        Map<String, Object> planMap = objectMapper.convertValue(persisted, Map.class);
        AssistantSuggestedProject saved = AssistantSuggestedProject.propose(run.getId(), run.getUserId(), planMap);
        suggestedProjects.save(saved);
        return saved;
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
