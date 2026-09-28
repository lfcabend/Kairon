package com.kairon.assistant.app;

import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.security.UserId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a queued {@code WEEKLY_SUMMARY}/{@code MONTHLY_SUMMARY} run's context
 * build + Anthropic call + persistence in the background. Deliberately a
 * separate bean from {@link AssistantRunService} — {@code @Async} on a
 * same-class self-call is silently a no-op under Spring's proxy-based AOP, so
 * the caller (the controller or {@code SummaryScheduler}) invokes this bean
 * only after {@code requestSummary}'s own transaction has committed
 * (docs/milestones/M9-execution-summaries.md D2).
 */
@Service
public class SummaryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SummaryGenerationService.class);

    private final AssistantRunRepository runs;
    private final SummaryContextBuilder contextBuilder;
    private final AnthropicClient anthropicClient;
    private final AssistantProperties properties;

    SummaryGenerationService(AssistantRunRepository runs, SummaryContextBuilder contextBuilder,
            AnthropicClient anthropicClient, AssistantProperties properties) {
        this.runs = runs;
        this.contextBuilder = contextBuilder;
        this.anthropicClient = anthropicClient;
        this.properties = properties;
    }

    @Async
    @Transactional
    public void generate(UUID runId) {
        AssistantRun run = runs.findById(runId).orElseThrow();
        SummaryContextBuilder.Context context = contextBuilder.build(
                new UserId(run.getUserId()), run.getKind(), run.getPeriodStart(), run.getPeriodEnd());
        run.start(context.inputSnapshot());
        runs.save(run);
        try {
            AnthropicClient.SummaryResult result = anthropicClient.generateSummary(
                    new AnthropicClient.SummaryRequest(context.systemPrompt(), context.userContent(),
                            run.getModel(), properties.summary().effort()));
            // D16 — the stats table is deterministic and rendered by our own code,
            // appended after the call returns; never authored by the model.
            String output = result.markdown() + "\n\n" + context.renderStatsTable();
            run.succeed(output, result.inputTokens(), result.outputTokens());
            log.info("Summary run {} succeeded userId={} tokens={}+{}",
                    run.getId(), run.getUserId(), result.inputTokens(), result.outputTokens());
        } catch (AssistantUpstreamException e) {
            run.fail(e.sanitizedDetail());
            log.warn("Summary run {} failed userId={} retryable={}", run.getId(), run.getUserId(), e.retryable());
        } finally {
            runs.save(run);
        }
    }
}
