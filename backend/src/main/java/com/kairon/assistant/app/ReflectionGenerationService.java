package com.kairon.assistant.app;

import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.UserAccountApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a queued {@code JOURNAL_REFLECTION} run's context build + Anthropic
 * call + persistence in the background — identical control flow to M9's
 * {@link SummaryGenerationService}, deliberately a separate bean from
 * {@link AssistantRunService} for the same reason (a same-class {@code @Async}
 * self-call is a Spring AOP no-op): the caller (the controller) dispatches
 * this bean only after {@code requestJournalReflection}'s own transaction has
 * committed (docs/milestones/M10-journal-reflection.md D3).
 */
@Service
public class ReflectionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionGenerationService.class);

    private final AssistantRunRepository runs;
    private final JournalReflectionContextBuilder contextBuilder;
    private final AnthropicClient anthropicClient;
    private final AssistantProperties properties;
    private final UserAccountApi accounts;

    ReflectionGenerationService(AssistantRunRepository runs, JournalReflectionContextBuilder contextBuilder,
            AnthropicClient anthropicClient, AssistantProperties properties, UserAccountApi accounts) {
        this.runs = runs;
        this.contextBuilder = contextBuilder;
        this.anthropicClient = anthropicClient;
        this.properties = properties;
        this.accounts = accounts;
    }

    @Async
    @Transactional
    public void generate(UUID runId) {
        AssistantRun run = runs.findById(runId).orElseThrow();
        UserId userId = new UserId(run.getUserId());
        String tone = accounts.assistantPreferences(userId).tone();
        JournalReflectionContextBuilder.Context context = contextBuilder.build(
                userId, run.getPeriodStart(), run.getPeriodEnd(), tone);
        run.start(context.inputSnapshot());
        runs.save(run);
        try {
            AnthropicClient.ReflectionResult result = anthropicClient.generateReflection(
                    new AnthropicClient.ReflectionRequest(context.systemPrompt(), context.userContent(),
                            run.getModel(), properties.journalReflection().effort()));
            // D11 — no appended table, unlike M9's summary output: the model's own
            // narrative is the entire response.
            run.succeed(result.markdown(), result.inputTokens(), result.outputTokens());
            log.info("Journal reflection run {} succeeded userId={} tokens={}+{}",
                    run.getId(), run.getUserId(), result.inputTokens(), result.outputTokens());
        } catch (AssistantUpstreamException e) {
            run.fail(e.sanitizedDetail());
            log.warn("Journal reflection run {} failed userId={} retryable={}",
                    run.getId(), run.getUserId(), e.retryable());
        } finally {
            runs.save(run);
        }
    }
}
