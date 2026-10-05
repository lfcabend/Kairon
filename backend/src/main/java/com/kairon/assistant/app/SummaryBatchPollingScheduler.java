package com.kairon.assistant.app;

import com.kairon.assistant.domain.AssistantBatch;
import com.kairon.assistant.domain.AssistantBatchStatus;
import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.repo.AssistantBatchRepository;
import com.kairon.assistant.repo.AssistantRunRepository;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixed-delay poller (M10 D22) over every {@code assistant_batch} not yet
 * {@code ENDED} — checks status via {@link AnthropicClient#pollBatch}, and
 * once a batch ends, reads every per-request result back
 * ({@link AnthropicClient#retrieveBatchResults}) and writes it onto its
 * matching {@code assistant_run} ({@code succeed}/{@code fail}). Kept as its
 * own small class rather than folded into {@link SummaryScheduler} (a
 * different trigger shape — fixed delay, not cron — and a genuinely different
 * responsibility: writing results back, not creating runs) and not built
 * kind-generic (only {@code WEEKLY_SUMMARY}/{@code MONTHLY_SUMMARY} are ever
 * batched today, D17).
 */
@Component
class SummaryBatchPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummaryBatchPollingScheduler.class);

    private final AssistantBatchRepository batches;
    private final AssistantRunRepository runs;
    private final AnthropicClient anthropicClient;

    SummaryBatchPollingScheduler(AssistantBatchRepository batches, AssistantRunRepository runs,
            AnthropicClient anthropicClient) {
        this.batches = batches;
        this.runs = runs;
        this.anthropicClient = anthropicClient;
    }

    @Scheduled(fixedDelayString = "${kairon.assistant.summary.batch-poll-interval:PT15M}")
    @Transactional
    void poll() {
        for (AssistantBatch batch : batches.findByStatusNot(AssistantBatchStatus.ENDED)) {
            pollOne(batch);
        }
    }

    private void pollOne(AssistantBatch batch) {
        AnthropicClient.BatchPollResult poll = anthropicClient.pollBatch(batch.getAnthropicBatchId());
        if (!poll.ended()) {
            batch.updateStatus(AssistantBatchStatus.valueOf(poll.status()));
            batches.save(batch);
            return;
        }

        int succeeded = 0;
        int failed = 0;
        for (AnthropicClient.BatchResultItem item : anthropicClient.retrieveBatchResults(batch.getAnthropicBatchId())) {
            AssistantRun run = runs.findById(parseRunId(item.customId())).orElse(null);
            if (run == null) {
                log.warn("Batch {} result for unknown run {}", batch.getAnthropicBatchId(), item.customId());
                continue;
            }
            if (item.succeeded()) {
                String statsTable = (String) run.getInputSnapshot().get("statsTable"); // D23
                run.succeed(item.markdown() + "\n\n" + statsTable, item.inputTokens(), item.outputTokens());
                succeeded++;
            } else {
                run.fail(item.errorDetail()); // D24 — one bad result doesn't fail the rest of the batch
                failed++;
            }
            runs.save(run);
        }
        batch.end();
        batches.save(batch);
        log.info("Batch {} ended: {} succeeded, {} failed", batch.getAnthropicBatchId(), succeeded, failed);
    }

    private static UUID parseRunId(String customId) {
        return UUID.fromString(customId);
    }
}
