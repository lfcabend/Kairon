package com.kairon.assistant.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

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
    private final AssistantProperties properties;
    private final UserAccountApi accounts;
    private final TodoSuggestionContextBuilder contextBuilder;
    private final AnthropicClient anthropicClient;
    private final Clock clock;

    public AssistantRunService(AssistantRunRepository runs, AssistantSuggestedTaskRepository suggestedTasks,
            AssistantProperties properties, UserAccountApi accounts, TodoSuggestionContextBuilder contextBuilder,
            AnthropicClient anthropicClient, Clock clock) {
        this.runs = runs;
        this.suggestedTasks = suggestedTasks;
        this.properties = properties;
        this.accounts = accounts;
        this.contextBuilder = contextBuilder;
        this.anthropicClient = anthropicClient;
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

    @Transactional(readOnly = true)
    public AssistantRunView get(UserId userId, UUID id) {
        AssistantRun run = require(userId, id);
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
